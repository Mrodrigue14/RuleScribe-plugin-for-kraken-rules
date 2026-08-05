package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.types.KrakenType
import com.kraken.plugin.types.KrakenTypeInference

/**
 * Signale deux incompatibilités de types que l'inférence sait établir avec
 * certitude.
 *
 * **Comparaison impossible.** `Type.isComparableWith` du moteur : les
 * numériques entre eux, les dates entre elles, les date-heures entre elles.
 * `Date` et `DateTime` ne sont **pas** comparables — c'est le piège classique
 * de KEL, et le seul moyen de s'en apercevoir aujourd'hui est de faire tourner
 * le moteur.
 *
 * **Argument de fonction mal typé.** Le catalogue des natives porte les types
 * KEL réels de chaque paramètre, donc la vérification est directe :
 * « Incompatible type ''{0}'' of function parameter at index {1} … » dans
 * `AstValidatingVisitor`.
 *
 * Comme en v0.9.0, tout ce qui touche à [KrakenType.Unknown] ou
 * [KrakenType.Any] est laissé passer : le plugin ne type pas tout, et un
 * diagnostic inventé coûte plus cher qu'un diagnostic manqué.
 */
class KrakenTypeMismatchInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when {
                    element is KrakenFunctionCall -> checkArguments(element, holder)
                    element.node?.elementType == KrakenTypes.VALUE_CHAIN -> checkComparison(element, holder)
                }
            }
        }

    /** `effectiveDate < createdOn` : Date contre DateTime, refusé par le moteur. */
    private fun checkComparison(chain: PsiElement, holder: ProblemsHolder) {
        // Nœuds AST : `children` de PSI exclut les feuilles, donc l'opérateur
        // lui-même n'y figure pas.
        val children = KrakenTypeInference.significantChildren(chain)
        for ((index, child) in children.withIndex()) {
            if (!isComparisonOperator(child)) continue
            val left = children.getOrNull(index - 1) ?: continue
            val right = children.getOrNull(index + 1) ?: continue
            val leftType = KrakenTypeInference.typeOf(left)
            val rightType = KrakenTypeInference.typeOf(right)
            if (!leftType.isKnown || !rightType.isKnown) continue
            // Le caractère « collection » d'une expression dépend de la
            // sémantique de projection et d'aplatissement de KEL, que cette
            // version ne modélise qu'en partie : on s'abstient dès qu'un côté
            // en est une.
            if (leftType is KrakenType.Array || rightType is KrakenType.Array) continue
            if (leftType.isComparableWith(rightType)) continue
            holder.registerProblem(
                chain,
                "Cannot compare '${leftType.displayName()}' with '${rightType.displayName()}'",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
            return
        }
    }

    private fun isComparisonOperator(element: PsiElement): Boolean {
        val type = element.node?.elementType
        if (type == KrakenTypes.LT || type == KrakenTypes.GT) return true
        return type == KrakenTypes.OP && element.text in COMPARISONS
    }

    /**
     * Seules les natives sont vérifiées : leurs types de paramètres viennent du
     * catalogue, donc du moteur. Une `Function` du projet déclare ses types en
     * DSL, mais un argument y est souvent une expression que l'inférence ne
     * couvre pas encore — la vérifier produirait surtout du bruit.
     */
    private fun checkArguments(call: KrakenFunctionCall, holder: ProblemsHolder) {
        val signature = KrakenFunctionCatalog.find(call.functionName, call.argumentCount) ?: return
        val args = call.node.findChildByType(KrakenTypes.CALL_ARGS)
            ?.getChildren(null)
            ?.filter { it.elementType == KrakenTypes.EXPRESSION }
            .orEmpty()

        for ((index, parameter) in signature.parameters.withIndex()) {
            val argument = args.getOrNull(index)?.psi ?: continue
            val expected = KrakenType.fromDslName(parameter.type)
            val actual = KrakenTypeInference.typeOf(argument)
            if (!actual.isKnown || expected.isDynamic) continue
            // Même raison : un paramètre tableau met en jeu la projection, que
            // l'inférence ne couvre pas assez pour trancher.
            if (expected is KrakenType.Array || actual is KrakenType.Array) continue
            if (expected.isAssignableFrom(actual)) continue
            holder.registerProblem(
                argument,
                "Incompatible type '${actual.displayName()}' for parameter " +
                    "'${parameter.name}' of '${call.functionName}', expected " +
                    "'${expected.displayName()}'",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }
    }

    private companion object {
        val COMPARISONS = setOf("<=", ">=", "=", "==", "!=")
    }
}
