package com.kraken.plugin.types

import com.intellij.psi.PsiElement
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl
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
        DATETIME_LITERAL.matches(text) -> KrakenType.DateTime
        DATE_LITERAL.matches(text) -> KrakenType.Date
        else -> KrakenType.Number
    }

    private val DATE_LITERAL = Regex("""\d{4}-\d{2}-\d{2}""")
    private val DATETIME_LITERAL = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z?""")

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

    /** Meaningful AST children: neither whitespace nor comments. */
    fun significantChildren(element: PsiElement): List<PsiElement> = element.node.getChildren(null)
        .filter { it.psi !is com.intellij.psi.PsiWhiteSpace && it.psi !is com.intellij.psi.PsiComment }
        .map { it.psi }

    fun isOperator(element: PsiElement): Boolean = when (element.node?.elementType) {
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

    private fun typeOfCall(call: KrakenFunctionCall?): KrakenType {
        if (call == null) return KrakenType.Unknown
        KrakenFunctionCatalog.find(call.functionName, call.argumentCount)?.let {
            return KrakenType.fromDslName(it.returnType)
        }
        val declared = call.reference?.resolve() as? KrakenFunctionDecl ?: return KrakenType.Unknown
        return declared.returnType?.let { KrakenType.fromDslName(it) } ?: KrakenType.Unknown
    }

    /**
     * Type of a declaration targeted by a reference: context field, child, or function
     * parameter. Expression variables (`set`, `for`) are not typed here, since that would
     * require following the source expression.
     */
    fun typeOfDeclaration(declaration: PsiElement?): KrakenType {
        val node = declaration?.node ?: return KrakenType.Unknown
        return when (node.elementType) {
            KrakenTypes.FIELD_DECL -> fieldType(declaration)

            // `Child Address` and `Child* Address`: the name is the context, and the star makes it
            // a collection.
            KrakenTypes.CHILD_DECL -> {
                val name = KrakenPsiUtil.identifiersOf(declaration.node).firstOrNull() ?: return KrakenType.Unknown
                val context = KrakenType.Context(name)
                if (node.findChildByType(KrakenTypes.STAR) != null) KrakenType.Array(context) else context
            }

            KrakenTypes.FUNCTION_PARAM ->
                KrakenPsiUtil.identifiersOf(declaration.node).firstOrNull()
                    ?.let { KrakenType.fromDslName(it) }
                    ?: KrakenType.Unknown

            else -> KrakenType.Unknown
        }
    }

    /** `Money limitAmount` → Money; `Coverage* items` → Coverage[]. */
    private fun fieldType(field: PsiElement): KrakenType {
        val names = KrakenPsiUtil.identifiersOf(field.node)
        if (names.size < 2) return KrakenType.Unknown
        val base = KrakenType.fromDslName(names.first())
        return if (field.node.findChildByType(KrakenTypes.STAR) != null) KrakenType.Array(base) else base
    }

    private fun singleExpressionIn(group: PsiElement): PsiElement? = significantChildren(group).singleOrNull { it.node.elementType == KrakenTypes.EXPRESSION }
}
