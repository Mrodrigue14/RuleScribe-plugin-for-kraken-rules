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
        val children = KrakenTypeInference.significantChildren(chain)
        for ((index, child) in children.withIndex()) {
            val operator = operatorName(child) ?: continue
            val left = children.getOrNull(index - 1) ?: continue
            val right = children.getOrNull(index + 1) ?: continue
            val leftType = KrakenTypeInference.typeOf(left)
            val rightType = KrakenTypeInference.typeOf(right)
            if (!leftType.isKnown || !rightType.isKnown) continue
            // Whether an expression is a collection depends on KEL projection and flattening,
            // which is only partly modelled, so skip when either side is one.
            if (leftType is KrakenType.Array || rightType is KrakenType.Array) continue

            val message = if (operator in EQUALITY_NAMES) {
                // Assignable either way, like the engine's `areVersusAssignable`.
                if (leftType.isAssignableFrom(rightType) || rightType.isAssignableFrom(leftType)) continue
                KrakenDiagnostic.NOT_SAME_TYPE.format(
                    operator,
                    leftType.displayName(),
                    rightType.displayName(),
                )
            } else {
                if (leftType.isComparableWith(rightType)) continue
                KrakenDiagnostic.NOT_COMPARABLE.format(
                    operator,
                    leftType.displayName(),
                    rightType.displayName(),
                )
            }
            holder.registerProblem(chain, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            return
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
            // Same reason: an array parameter involves projection, which inference cannot decide.
            if (expected is KrakenType.Array || actual is KrakenType.Array) continue
            if (expected.isAssignableFrom(actual)) continue
            holder.registerProblem(
                argument,
                KrakenDiagnostic.INCOMPATIBLE_PARAMETER.format(
                    actual.displayName(),
                    index,
                    call.functionName,
                    expected.displayName(),
                ),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
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
    }
}
