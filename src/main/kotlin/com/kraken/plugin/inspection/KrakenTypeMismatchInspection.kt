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
 * Signale les incompatibilités de types que l'inférence sait établir avec
 * certitude, sur les trois cas que `AstValidatingVisitor` distingue.
 *
 * **Ordre impossible** (`<`, `>`, `<=`, `>=`). `validateBinaryComparison` du
 * moteur exige `Type.isComparableWith` : les numériques entre eux, les dates
 * entre elles, les date-heures entre elles. `Date` contre `DateTime` est le
 * piège classique de KEL, et deux `String` ne sont pas ordonnables non plus.
 *
 * **Égalité entre types différents** (`=`, `!=`). `validateTypeCompatibility`
 * applique un critère plus large — chaque côté doit être assignable à l'autre
 * — parce que comparer deux `String` est parfaitement légitime là où les
 * ordonner ne l'est pas. Confondre les deux critères, comme le faisait la
 * v0.10.x, laissait passer `a < b` sur deux `String`.
 *
 * **Argument de fonction mal typé.** Le catalogue des natives porte les types
 * KEL réels de chaque paramètre, donc la vérification est directe.
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
            val operator = operatorName(child) ?: continue
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

            val message = if (operator in EQUALITY_NAMES) {
                // Assignable dans un sens ou dans l'autre, comme
                // `areVersusAssignable` du moteur.
                if (leftType.isAssignableFrom(rightType) || rightType.isAssignableFrom(leftType)) continue
                KrakenDiagnostic.NOT_SAME_TYPE.format(
                    operator, leftType.displayName(), rightType.displayName()
                )
            } else {
                if (leftType.isComparableWith(rightType)) continue
                KrakenDiagnostic.NOT_COMPARABLE.format(
                    operator, leftType.displayName(), rightType.displayName()
                )
            }
            holder.registerProblem(chain, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            return
        }
    }

    /**
     * Nom du nœud tel que le moteur l'imprime dans ses messages (`NodeType`
     * rend son `name`, pas le symbole), ou null si l'élément n'est pas un
     * opérateur de comparaison.
     */
    private fun operatorName(element: PsiElement): String? = when (element.node?.elementType) {
        KrakenTypes.LT -> "LessThan"
        KrakenTypes.GT -> "MoreThan"
        KrakenTypes.OP -> OPERATOR_NAMES[element.text]
        else -> null
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
                KrakenDiagnostic.INCOMPATIBLE_PARAMETER.format(
                    actual.displayName(), index, call.functionName, expected.displayName()
                ),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }
    }

    private companion object {
        /** Symbole KEL → nom du `NodeType` correspondant côté moteur. */
        val OPERATOR_NAMES = mapOf(
            "<=" to "LessThanOrEquals",
            ">=" to "MoreThanOrEquals",
            "=" to "Equals",
            "==" to "Equals",
            "!=" to "NotEquals",
        )

        /** Ceux qui relèvent de l'assignabilité, pas de l'ordre. */
        val EQUALITY_NAMES = setOf("Equals", "NotEquals")
    }
}
