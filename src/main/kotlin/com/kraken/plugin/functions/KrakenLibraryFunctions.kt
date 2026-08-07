package com.kraken.plugin.functions

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope

/**
 * Fonctions `@ExpressionFunction` de classes enregistrées comme
 * `FunctionLibrary` Kraken — que ce soit dans le projet ou dans une
 * dépendance Maven livrée en `.class`.
 *
 * Le moteur découvre ces classes uniquement via `ServiceLoader.load` (voir
 * `FunctionRegistry.reload()`), donc uniquement celles listées dans
 * `META-INF/services/kraken.el.functionregistry.FunctionLibrary`. Chercher
 * `@ExpressionFunction` sur tout le classpath (version précédente, via
 * `AnnotatedElementsSearch`) revenait à parcourir l'index d'annotations de
 * tout le classpath pour ne garder qu'une poignée de résultats : sur un
 * projet d'entreprise à des centaines de JAR, cette recherche ne terminait
 * jamais dans le budget d'une passe de highlighting (annulée puis
 * recommencée à chaque frappe), et le correctif semblait ne rien faire.
 *
 * On va donc directement au fichier SPI plutôt que par un index de contenu :
 * pour chaque racine du classpath (source du projet ou racine de JAR), un
 * seul essai sur le chemin relatif fixe `META-INF/services/...` — pas de
 * recherche, une résolution directe via `VirtualFile.findFileByRelativePath`.
 * Le PSI Java ne sert qu'à l'étape qui en a réellement besoin : résoudre
 * chaque classe listée pour lire ses méthodes annotées. Jamais de chargement
 * de ces classes en mémoire, seulement une résolution PSI — d'où la
 * dépendance *optionnelle* à `com.intellij.java`.
 */
internal object KrakenLibraryFunctions {

    private const val SPI_RELATIVE_PATH = "META-INF/services/kraken.el.functionregistry.FunctionLibrary"
    private const val ANNOTATION_FQN = "kraken.el.functionregistry.ExpressionFunction"

    /** Racines des sources du projet (pas des dépendances). */
    fun projectNames(project: Project): Set<String> =
        names(project, ProjectRootManager.getInstance(project).contentSourceRoots.asIterable(), GlobalSearchScope.projectScope(project))

    /** Racines des JAR de bibliothèques (pas des sources du projet). */
    fun libraryNames(project: Project): Set<String> =
        names(
            project,
            OrderEnumerator.orderEntries(project).librariesOnly().classes().roots.asIterable(),
            ProjectScope.getLibrariesScope(project)
        )

    private fun names(project: Project, roots: Iterable<VirtualFile>, resolveScope: GlobalSearchScope): Set<String> {
        val facade = JavaPsiFacade.getInstance(project)
        val names = LinkedHashSet<String>()
        for (root in roots) {
            val spiFile = root.findFileByRelativePath(SPI_RELATIVE_PATH) ?: continue
            for (fqcn in registeredClassNames(spiFile)) {
                val psiClass = facade.findClass(fqcn, resolveScope) ?: continue
                for (method in psiClass.methods) {
                    val annotation = method.getAnnotation(ANNOTATION_FQN) ?: continue
                    val value = annotation.findAttributeValue("value")
                    val name = facade.constantEvaluationHelper.computeConstantExpression(value) as? String ?: continue
                    names += name
                }
            }
        }
        return names
    }

    /** Format standard `java.util.ServiceLoader` : un FQN par ligne, `#` ouvre un commentaire. */
    private fun registeredClassNames(spiFile: VirtualFile): List<String> =
        VfsUtilCore.loadText(spiFile).lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toList()
}
