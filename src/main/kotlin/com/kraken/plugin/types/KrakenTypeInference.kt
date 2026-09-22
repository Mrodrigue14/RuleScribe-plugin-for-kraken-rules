package com.kraken.plugin.types

import com.intellij.psi.PsiElement
import com.kraken.plugin.parser.KrakenLexer
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenChildDecl
import com.kraken.plugin.psi.KrakenContextMember
import com.kraken.plugin.psi.KrakenFieldDecl
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionParam
import com.kraken.plugin.psi.KrakenPathSegment
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRefExpr

/**
 * Infers the type of a KEL expression.
 *
 * Deliberately partial. The engine types everything because it has the resolved model;
 * here every uncovered case returns [KrakenType.Unknown], and checks that meet it
 * abstain rather than assert something wrong.
 */
object KrakenTypeInference {

    fun typeOf(element: PsiElement?): KrakenType {
        if (element == null) return KrakenType.Unknown
        return when (element.node?.elementType) {
            KrakenTypes.EXPRESSION, KrakenTypes.VALUE_CHAIN -> typeOfChain(element)

            KrakenTypes.POSTFIX_EXPR -> typeOfPostfix(element)

            KrakenTypes.GROUP_EXPR -> typeOf(singleExpressionIn(element))

            KrakenTypes.FUNCTION_CALL -> typeOfCall(element as? KrakenFunctionCall)

            KrakenTypes.REF_EXPR -> typeOfDeclaration((element as KrakenRefExpr).reference?.resolve())

            KrakenTypes.STRING -> KrakenType.String

            // The lexer files date literals under NUMBER_LIT; only the text tells them apart.
            KrakenTypes.NUMBER_LIT -> literalType(element.text)

            KrakenTypes.TRUE_KW, KrakenTypes.FALSE_KW -> KrakenType.Boolean

            KrakenTypes.NULL_KW -> KrakenType.Any

            else -> KrakenType.Unknown
        }
    }

    private fun literalType(text: String): KrakenType = when {
        KrakenLexer.DATE_TIME_LITERAL.matches(text) -> KrakenType.DateTime
        KrakenLexer.DATE_LITERAL.matches(text) -> KrakenType.Date
        else -> KrakenType.Number
    }

    /**
     * A chain of values only has a safe type when it contains no operator; otherwise each
     * operator would need to be modelled.
     */
    private fun typeOfChain(element: PsiElement): KrakenType {
        // Walk AST nodes, not `children`, which excludes leaves: operators and literals would be
        // invisible, and `policyCd + 1` would look like a plain String.
        val parts = significantChildren(element)
        if (parts.any { isOperator(it) }) return KrakenType.Unknown
        return parts.singleOrNull()?.let { typeOf(it) } ?: KrakenType.Unknown
    }

    private fun significantChildren(element: PsiElement): List<PsiElement> = KrakenPsiUtil.significantChildren(element)

    private fun isOperator(element: PsiElement): Boolean = when (element.node?.elementType) {
        KrakenTypes.OP, KrakenTypes.PIPE, KrakenTypes.LT, KrakenTypes.GT, KrakenTypes.STAR, KrakenTypes.COLON,
        KrakenTypes.IN_KW, KrakenTypes.IS_KW, KrakenTypes.AND_KW, KrakenTypes.OR_KW,
        KrakenTypes.INSTANCEOF_KW, KrakenTypes.TYPEOF_KW, KrakenTypes.SATISFIES_KW,
        KrakenTypes.MATCHES_KW,
        -> true

        else -> false
    }

    /**
     * `a.b.c`: the type of the last resolved segment, but projected over a collection it
     * becomes a collection. In KEL, `coverages.limitAmount` on `Coverage[]` is `Money[]`.
     */
    private fun typeOfPostfix(element: PsiElement): KrakenType {
        // Direct segments of this chain only. findChildrenOfType is recursive and would also
        // return segments inside call arguments, typing `Count(Vehicle.model)` as `model`.
        val segments = directSegments(element)
        if (segments.isNotEmpty()) {
            val last = segments.last()
            if (last.isCall) return KrakenType.Unknown
            val leafType = typeOfDeclaration(last.reference?.resolve())
            if (!leafType.isKnown) return KrakenType.Unknown
            return if (projectsOverACollection(element, last)) wrap(leafType) else leafType
        }
        val children = significantChildren(element)
        val head = children.firstOrNull() ?: return KrakenType.Unknown
        // A bracket reduces a collection to its element.
        val bracketed = children.any { it.node.elementType == KrakenTypes.BRACKET_ACCESS }
        val headType = typeOf(head)
        return if (bracketed && headType is KrakenType.Array) headType.element else headType
    }

    private fun wrap(type: KrakenType): KrakenType = if (type is KrakenType.Array) type else KrakenType.Array(type)

    /**
     * True if a link before [last] denotes a collection: the chain is then a projection and
     * its result a collection.
     */
    private fun projectsOverACollection(chain: PsiElement, last: KrakenPathSegment): Boolean {
        val head = significantChildren(chain).firstOrNull()
        return (head != null && head !is KrakenPathSegment && typeOf(head) is KrakenType.Array) ||
            directSegments(chain)
                .takeWhile { it !== last }
                .any { !it.isCall && typeOfDeclaration(it.reference?.resolve()) is KrakenType.Array }
    }

    /** Access segments of this chain, without descending into calls. */
    private fun directSegments(chain: PsiElement): List<KrakenPathSegment> = significantChildren(chain)
        .filter { it.node.elementType == KrakenTypes.DOT_ACCESS }
        .mapNotNull { access ->
            significantChildren(access).filterIsInstance<KrakenPathSegment>().firstOrNull()
        }

    private fun typeOfCall(call: KrakenFunctionCall?): KrakenType = call?.target()?.returnType?.let { KrakenType.fromDslName(it) } ?: KrakenType.Unknown

    /**
     * Type of a declaration targeted by a reference: context field, child, or function
     * parameter. Expression variables (`set`, `for`) are not typed here, since that would
     * require following the source expression.
     */
    fun typeOfDeclaration(declaration: PsiElement?): KrakenType = when (declaration) {
        is KrakenContextMember -> memberType(declaration)
        is KrakenFunctionParam -> declaration.typeName?.let { KrakenType.fromDslName(it) } ?: KrakenType.Unknown
        else -> KrakenType.Unknown
    }

    /** `Money limitAmount` → Money; `Child* Vehicle` → Vehicle[]. */
    private fun memberType(member: KrakenContextMember): KrakenType {
        val base = when (member) {
            is KrakenFieldDecl -> member.typeName?.let { KrakenType.fromDslName(it) }
            is KrakenChildDecl -> member.name?.let { KrakenType.Context(it) }
        } ?: return KrakenType.Unknown
        return if (member.isCollection) KrakenType.Array(base) else base
    }

    private fun singleExpressionIn(group: PsiElement): PsiElement? = significantChildren(group).singleOrNull { it.node.elementType == KrakenTypes.EXPRESSION }
}
