package com.kraken.plugin.functions

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

/**
 * Fonctions `@ExpressionFunction` déclarées dans une bibliothèque Maven —
 * classes compilées d'une dépendance, jamais présentes en `.java` dans le
 * projet. [KrakenProjectFunctions] ne les voit pas : sa lecture par expression
 * régulière ne porte que sur les sources du module.
 *
 * On délègue donc au PSI Java, seul capable de trouver une annotation sur une
 * classe compilée (bytecode) au même titre qu'une classe source, via l'index
 * du plugin Java plutôt qu'un parcours du classpath fait main. D'où la
 * dépendance *optionnelle* à `com.intellij.java` : cette classe n'est chargée,
 * donc jamais référencée par la JVM, que si ce module est présent — on garde
 * ainsi le plugin fonctionnel sur toute plateforme sans support Java.
 */
internal object KrakenLibraryFunctions {

    private const val ANNOTATION_FQN = "kraken.el.functionregistry.ExpressionFunction"

    fun names(project: Project): Set<String> {
        val scope = GlobalSearchScope.allScope(project)
        val annotationClass = JavaPsiFacade.getInstance(project).findClass(ANNOTATION_FQN, scope)
            ?: return emptySet() // Ni le projet ni ses dépendances ne connaissent Kraken.

        val names = LinkedHashSet<String>()
        for (method in AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)) {
            val annotation = method.getAnnotation(ANNOTATION_FQN) ?: continue
            val value = annotation.findAttributeValue("value")
            val name = JavaPsiFacade.getInstance(project).constantEvaluationHelper
                .computeConstantExpression(value) as? String ?: continue
            names += name
        }
        return names
    }
}
