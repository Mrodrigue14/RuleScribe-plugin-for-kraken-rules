package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.kraken.plugin.parser.KrakenTypes

/**
 * Bare identifier in a KEL expression: `limitAmount`, `Policy`, `total`.
 *
 * It can denote an expression variable, a field of the `On` target context, or a
 * project context; [KrakenScopeResolver] decides, in engine order.
 */
class KrakenRefExpr(node: ASTNode) : ASTWrapperPsiElement(node) {

    val referenceName: String
        get() = text.trim()

    override fun getReference(): PsiReference? = if (referenceName.isEmpty()) null else KrakenIdentifierReference(this)
}

/**
 * Segment of an access chain: `postalCode` in `AddressInfo.postalCode`.
 *
 * Resolves only when the previous segment denotes a known context. Without type
 * inference, a head of unknown type stops the chain instead of guessing.
 */
class KrakenPathSegment(node: ASTNode) : ASTWrapperPsiElement(node) {

    /** A segment followed by arguments is a method call, not a field. */
    val isCall: Boolean
        get() = node.findChildByType(KrakenTypes.CALL_ARGS) != null

    val segmentName: String
        get() = node.firstChildNode?.text?.trim().orEmpty()

    override fun getReference(): PsiReference? = if (isCall || segmentName.isEmpty()) null else KrakenPathSegmentReference(this)

    /**
     * Context this segment belongs to, derived from the previous link.
     *
     * The chain lives in a `postfix_expr`: a `primary_expr` followed by `dot_access` nodes.
     * Walking from the head, each segment must denote a context for the next to resolve.
     */
    fun owningContext(): String? {
        val access = parent?.takeIf { it.node.elementType == KrakenTypes.DOT_ACCESS } ?: return null
        val chain = access.parent?.takeIf { it.node.elementType == KrakenTypes.POSTFIX_EXPR } ?: return null
        return KrakenScopeResolver.contextBefore(chain, this)
    }
}

class KrakenIdentifierReference(element: KrakenRefExpr) : PsiReferenceBase<KrakenRefExpr>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? = KrakenScopeResolver.resolve(element, element.referenceName)

    override fun getVariants(): Array<Any> = KrakenScopeResolver.visibleNames(element).toTypedArray()
}

class KrakenPathSegmentReference(element: KrakenPathSegment) :
    PsiReferenceBase<KrakenPathSegment>(
        element,
        TextRange(0, element.segmentName.length),
        true,
    ) {

    override fun resolve(): PsiElement? {
        val context = element.owningContext() ?: return null
        return KrakenScopeResolver.findField(element, context, element.segmentName)
    }

    override fun getVariants(): Array<Any> {
        val context = element.owningContext() ?: return emptyArray()
        return KrakenContexts.contextFieldNames(element.containingFile, context).toTypedArray()
    }
}
