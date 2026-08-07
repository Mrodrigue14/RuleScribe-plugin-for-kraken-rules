package com.kraken.plugin.functions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Fonctions natives déclarées par le projet lui-même ou par une bibliothèque
 * Maven — dans les deux cas, le moteur les met en portée sans qu'aucune
 * signature ne soit déclarée dans le DSL. Une analyse statique ne peut donc
 * pas les découvrir par le seul examen des `.rules` : c'est ce qui faisait
 * signaler des centaines d'appels parfaitement valides dans un projet
 * d'entreprise (issue #43).
 *
 * Deux sources, mises en cache séparément parce qu'elles ne changent pas à
 * la même fréquence :
 *
 * - [projectNames] — union de [KrakenLibraryFunctions] (résolution SPI, voir
 *   sa KDoc) et de [scan] (mot-index, texte brut). Les deux tournent quand le
 *   plugin Java est présent : `FunctionRegistry.reload()` de l'OSS Kraken ne
 *   connaît que `ServiceLoader.load`, mais rien ne garantit qu'un hôte
 *   d'entreprise (ex. une plateforme applicative maison construite sur
 *   Kraken) enregistre ses bibliothèques de la même façon — on ne l'a pas
 *   vérifié, donc on ne parie pas dessus. Le scan texte est déjà appuyé sur
 *   un index de mots (peu coûteux) ; le garder en plus de SPI coûte presque
 *   rien et évite qu'une hypothèse fausse sur le mécanisme d'enregistrement
 *   fasse regresser une fonction du projet vers « inconnue ». Dépend de
 *   [PsiModificationTracker] : un développeur peut éditer sa propre classe
 *   d'une frappe à l'autre.
 * - [libraryNames] — résolution SPI seule, scope bibliothèques. Dépend
 *   uniquement de [ProjectRootManager]. C'est le point qui a cassé les deux
 *   premières tentatives (0.10.1, 0.10.2) : en la faisant dépendre de
 *   `PsiModificationTracker` comme le reste, une frappe n'importe où
 *   invalidait un calcul qui, sur un classpath de centaines de JAR, prenait
 *   plus longtemps que l'intervalle entre deux frappes — le calcul ne
 *   terminait donc jamais, silencieusement. Les dépendances Maven ne
 *   changent qu'au sync du projet ; les lier à ProjectRootManager seul règle
 *   ça structurellement, pas juste en rendant la recherche plus rapide. Pas
 *   de repli texte ici : le bytecode d'un JAR ne se lit pas par regex.
 *
 * Conséquence assumée : une fonction est connue **à toute arité**. Sans
 * analyser la signature Java, on ne peut pas vérifier le nombre de
 * paramètres, et prétendre le contraire produirait de faux diagnostics.
 *
 * Chaque calcul journalise son résultat (`Help > Show Log`) : le nombre de
 * fonctions trouvées et le temps pris. Les deux tentatives précédentes n'ont
 * eu que l'impression visuelle de l'utilisateur comme signal, ce qui a permis
 * à 0.10.2 de sembler fonctionner alors qu'il ne terminait jamais.
 */
object KrakenProjectFunctions {

    private val LOG = Logger.getInstance(KrakenProjectFunctions::class.java)
    private val PROJECT_KEY = Key.create<CachedValue<Set<String>>>("kraken.project.functions")
    private val LIBRARY_KEY = Key.create<CachedValue<Set<String>>>("kraken.library.functions")

    fun names(project: Project): Set<String> = projectNames(project) + libraryNames(project)

    fun contains(project: Project, name: String): Boolean = name in names(project)

    private fun projectNames(project: Project): Set<String> =
        CachedValuesManager.getManager(project).getCachedValue(project, PROJECT_KEY, {
            val start = System.currentTimeMillis()
            val names = try {
                KrakenLibraryFunctions.projectNames(project) + scan(project)
            } catch (e: LinkageError) {
                scan(project) // pas de plugin Java : repli texte, sources du projet seulement
            }
            LOG.info("Kraken: ${names.size} project function(s) discovered in ${System.currentTimeMillis() - start}ms")
            CachedValueProvider.Result.create(names, PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    /** Isolée dans son propre cache : ne doit se recalculer qu'au changement des dépendances, pas à chaque frappe. */
    private fun libraryNames(project: Project): Set<String> =
        CachedValuesManager.getManager(project).getCachedValue(project, LIBRARY_KEY, {
            val start = System.currentTimeMillis()
            val names = try {
                KrakenLibraryFunctions.libraryNames(project)
            } catch (e: LinkageError) {
                emptySet()
            }
            LOG.info("Kraken: ${names.size} library function(s) discovered in ${System.currentTimeMillis() - start}ms")
            CachedValueProvider.Result.create(names, ProjectRootManager.getInstance(project))
        }, false)

    /** Repli sans PSI Java : mot-index de la plateforme, pas de lecture fichier par fichier. */
    private fun scan(project: Project): Set<String> {
        val scope = GlobalSearchScope.projectScope(project)
        val names = LinkedHashSet<String>()
        PsiSearchHelper.getInstance(project).processAllFilesWithWord(WORD, scope, { file ->
            val virtualFile = file.virtualFile
            if (virtualFile != null &&
                virtualFile.extension.equals("java", ignoreCase = true) &&
                virtualFile.length <= MAX_FILE_SIZE
            ) {
                for (match in PATTERN.findAll(file.text)) {
                    names += match.groupValues[1]
                }
            }
            true
        }, true)
        return names
    }

    private const val WORD = "ExpressionFunction"
    private const val MAX_FILE_SIZE = 1_000_000L
    private val PATTERN = Regex("""@ExpressionFunction\s*\(\s*"([^"]+)"\s*\)""")
}

private typealias CachedValue<T> = com.intellij.psi.util.CachedValue<T>
