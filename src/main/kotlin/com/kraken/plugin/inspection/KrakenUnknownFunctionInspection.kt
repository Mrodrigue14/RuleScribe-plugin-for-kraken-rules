package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenPsiUtil

/**
 * Signale un appel qui ne correspond à aucune fonction connue.
 *
 * Le moteur identifie une fonction par `(nom, nombre de paramètres)` et refuse
 * l'expression sinon : « Function ''{0}'' with {1} parameter(s) does not
 * exist. » (`kraken.el.ast.validation.AstValidatingVisitor`). On reprend le
 * même critère, sur les trois provenances que le moteur fusionne :
 * fonctions natives Java, `Function` déclarée avec un corps, signature
 * `Function` sans corps.
 *
 * Quand le nom existe mais pas avec cette arité, le message le dit — c'est
 * l'erreur la plus fréquente, et la distinguer évite de chercher une faute de
 * frappe qui n'existe pas.
 *
 * **Angle mort assumé.** `ScopeBuilder` peuple la portée avec
 * `FunctionRegistry.getNativeFunctions(…)`, qui ne retient que les méthodes
 * annotées `@Native`. Une `FunctionLibrary` maison **sans** `@Native` doit donc
 * être déclarée par une signature `Function` dans un `.rules`, que le plugin
 * voit — pas de faux positif. Mais une bibliothèque maison **avec** `@Native`
 * est visible du moteur sans rien déclarer, et un plugin d'analyse statique,
 * qui n'exécute rien et ne lit pas le classpath, ne peut pas la découvrir.
 * D'où [additionalNativeFunctions] : les projets concernés y listent leurs
 * noms plutôt que de désactiver l'inspection entière.
 */
class KrakenUnknownFunctionInspection : LocalInspectionTool() {

    /**
     * Noms de fonctions natives supplémentaires, fournies par une
     * `FunctionLibrary` `@Native` propre au projet. Champ public : c'est ainsi
     * que la plateforme sérialise les options d'inspection dans le profil.
     */
    @JvmField
    var additionalNativeFunctions: MutableList<String> = mutableListOf()

    override fun getOptionsPane(): OptPane = OptPane.pane(
        OptPane.stringList(
            "additionalNativeFunctions",
            "Additional native functions provided by the project (@Native Java libraries)"
        )
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is KrakenFunctionCall) return
                val name = element.functionName
                if (name.isEmpty()) return
                val arity = element.argumentCount
                if (element.isResolvable()) return
                if (name in additionalNativeFunctions) return

                val knownArities = knownArities(element, name)
                val message = if (knownArities.isEmpty()) {
                    "Unknown function '$name'"
                } else {
                    "Function '$name' with $arity parameter(s) does not exist " +
                        "(declared with ${knownArities.sorted().joinToString(" or ")})"
                }
                holder.registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
        }

    /** Arités connues pour ce nom, toutes provenances confondues. */
    private fun knownArities(call: KrakenFunctionCall, name: String): Set<Int> =
        KrakenFunctionCatalog.byName(name).map { it.parameters.size }.toSet() +
            KrakenPsiUtil.findFunctionsVisible(call).filter { it.name == name }.map { it.arity }
}
