package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionTarget
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.types.KrakenType
import com.kraken.plugin.types.KrakenTypeInference

/**
 * Reports type mismatches that inference can establish with certainty, in the three
 * cases `AstValidatingVisitor` distinguishes.
 *
 * Ordering (`<`, `>`, `<=`, `>=`): the engine's `validateBinaryComparison` requires
 * `Type.isComparableWith`, which holds for numbers, dates, and date-times among
 * themselves. `Date` against `DateTime` is the classic KEL trap, and two `String`s
 * cannot be ordered either.
 *
 * Equality (`=`, `!=`): `validateTypeCompatibility` is broader, requiring each side to
 * be assignable to the other, since comparing two `String`s is legitimate where
 * ordering them is not.
 *
 * Function arguments: the native catalogue has each parameter's real KEL type, so the
 * check is direct.
 *
 * Anything involving [KrakenType.Unknown] or [KrakenType.Any] passes: the plugin does
 * not type everything, and an invented diagnostic costs more than a missed one.
 */
class KrakenTypeMismatchInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            when {
                element is KrakenFunctionCall -> checkArguments(element, holder)
                element.node?.elementType == KrakenTypes.VALUE_CHAIN -> checkComparison(element, holder)
            }
        }
    }

    /** `effectiveDate < createdOn`: Date against DateTime, rejected by the engine. */
    private fun checkComparison(chain: PsiElement, holder: ProblemsHolder) {
        // AST nodes: PSI `children` excludes leaves, so the operator would be missing.
        val parts = KrakenPsiUtil.significantChildren(chain)
        val message = parts.indices.firstNotNullOfOrNull { mismatchAt(parts, it) } ?: return
        holder.registerProblem(chain, message)
    }

    /**
     * What the comparison at [index] of [parts] violates, or null.
     *
     * A value chain is flat, without precedence, so a neighbour is only taken as a whole
     * operand when the operator beyond it is a logical one: in `"ID" + num = code`, the
     * left side of `=` is `"ID" + num`, not `num`.
     */
    private fun mismatchAt(parts: List<PsiElement>, index: Int): String? {
        val operator = operatorName(parts[index]) ?: return null
        if (!isWholeOperand(parts, index - 1, outward = -1) || !isWholeOperand(parts, index + 1, outward = 1)) return null
        val left = KrakenTypeInference.typeOf(parts[index - 1])
        val right = KrakenTypeInference.typeOf(parts[index + 1])
        if (!left.isKnown || !right.isKnown) return null
        // Whether an expression is a collection depends on KEL projection and flattening,
        // which is only partly modelled, so skip when either side is one.
        if (left is KrakenType.Array || right is KrakenType.Array) return null
        return if (operator in EQUALITY_NAMES) {
            // Assignable either way, like the engine's `areVersusAssignable`.
            val compatible = left.isAssignableFrom(right) || right.isAssignableFrom(left)
            KrakenDiagnostic.NOT_SAME_TYPE.format(operator, left.displayName, right.displayName).takeUnless { compatible }
        } else {
            KrakenDiagnostic.NOT_COMPARABLE.format(operator, left.displayName, right.displayName).takeUnless { left.isComparableWith(right) }
        }
    }

    private fun isWholeOperand(parts: List<PsiElement>, operand: Int, outward: Int): Boolean {
        if (operand !in parts.indices) return false
        val beyond = parts.getOrNull(operand + outward) ?: return true
        return when (beyond.node.elementType) {
            KrakenTypes.AND_KW, KrakenTypes.OR_KW -> true
            KrakenTypes.OP -> beyond.text in LOGICAL_OPERATORS
            else -> false
        }
    }

    /**
     * Node name as the engine prints it in messages (`NodeType` renders its `name`, not the
     * symbol), or null if [element] is not a comparison operator.
     */
    private fun operatorName(element: PsiElement): String? = when (element.node?.elementType) {
        KrakenTypes.LT -> "LessThan"
        KrakenTypes.GT -> "MoreThan"
        KrakenTypes.OP -> OPERATOR_NAMES[element.text]
        else -> null
    }

    /**
     * Only natives are checked, since their parameter types come from the engine
     * catalogue. Arguments to project `Function`s are often expressions inference does not
     * cover yet, so checking them would mostly produce noise.
     */
    private fun checkArguments(call: KrakenFunctionCall, holder: ProblemsHolder) {
        val signature = (call.target() as? KrakenFunctionTarget.Native)?.function ?: return
        val args = call.arguments
        for ((index, parameter) in signature.parameters.withIndex()) {
            val argument = args.getOrNull(index) ?: continue
            val expected = KrakenType.fromDslName(parameter.type)
            val actual = KrakenTypeInference.typeOf(argument)
            if (!actual.isKnown || expected.isDynamic) continue
            // Same reason: an array parameter involves projection, which inference cannot decide.
            if (expected is KrakenType.Array || actual is KrakenType.Array) continue
            if (expected.isAssignableFrom(actual)) continue
            holder.registerProblem(
                argument,
                KrakenDiagnostic.INCOMPATIBLE_PARAMETER.format(
                    actual.displayName,
                    index,
                    call.functionName,
                    expected.displayName,
                ),
            )
        }
    }

    private companion object {
        /** KEL symbol → engine `NodeType` name. */
        val OPERATOR_NAMES = mapOf(
            "<=" to "LessThanOrEquals",
            ">=" to "MoreThanOrEquals",
            "=" to "Equals",
            "==" to "Equals",
            "!=" to "NotEquals",
        )

        /** Operators judged by assignability rather than ordering. */
        val EQUALITY_NAMES = setOf("Equals", "NotEquals")

        /** The only operators that bind looser than a comparison. */
        val LOGICAL_OPERATORS = setOf("&&", "||")
    }
}
