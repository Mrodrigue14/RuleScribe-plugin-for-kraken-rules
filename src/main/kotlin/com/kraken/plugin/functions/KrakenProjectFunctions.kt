package com.kraken.plugin.functions

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Fonctions natives déclarées par le projet lui-même.
 *
 * Le moteur met automatiquement en portée toute méthode Java annotée
 * `@ExpressionFunction` dont la bibliothèque porte `@Native` — sans qu'aucune
 * signature ne soit déclarée dans le DSL. Une analyse statique ne peut donc pas
 * les découvrir par le seul examen des `.rules` : c'est ce qui faisait signaler
 * des centaines d'appels parfaitement valides dans un projet d'entreprise
 * (issue #43).
 *
 * On lit donc les sources Java du projet. Volontairement par expression
 * régulière plutôt qu'à travers le PSI Java : le plugin resterait sinon
 * dépendant du plugin Java, alors qu'il doit fonctionner dans n'importe quel
 * IDE de la plateforme. L'annotation est déclarative et sa forme est stable,
 * ce qui rend le compromis raisonnable — on récupère des noms, pas des
 * signatures.
 *
 * Conséquence assumée : une fonction du projet est connue **à toute arité**.
 * Sans analyser la signature Java, on ne peut pas vérifier le nombre de
 * paramètres, et prétendre le contraire produirait de faux diagnostics.
 */
object KrakenProjectFunctions {

    private val KEY = Key.create<CachedValue<Set<String>>>("kraken.project.functions")

    /** Noms déclarés par `@ExpressionFunction` dans les sources Java du projet. */
    fun names(project: Project): Set<String> =
        CachedValuesManager.getManager(project).getCachedValue(project, KEY, {
            CachedValueProvider.Result.create(scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    fun contains(project: Project, name: String): Boolean = name in names(project)

    private fun scan(project: Project): Set<String> {
        val scope = GlobalSearchScope.projectScope(project)
        val manager = PsiManager.getInstance(project)
        val names = LinkedHashSet<String>()
        for (file in FilenameIndex.getAllFilesByExt(project, "java", scope)) {
            if (file.length > MAX_FILE_SIZE) continue
            val text = manager.findFile(file)?.text ?: continue
            // Filtre bon marché avant la regex : la quasi-totalité des fichiers
            // d'un projet d'entreprise ne contient pas l'annotation.
            if (!text.contains(ANNOTATION)) continue
            for (match in PATTERN.findAll(text)) {
                names += match.groupValues[1]
            }
        }
        return names
    }

    private const val ANNOTATION = "@ExpressionFunction"
    private const val MAX_FILE_SIZE = 1_000_000L
    private val PATTERN = Regex("""@ExpressionFunction\s*\(\s*"([^"]+)"\s*\)""")
}

private typealias CachedValue<T> = com.intellij.psi.util.CachedValue<T>
