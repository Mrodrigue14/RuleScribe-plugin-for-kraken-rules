package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes

/**
 * `Function Name(Type param) : ReturnType { body }` declaration.
 *
 * With a body, the implementation is KEL. Without one it is a signature declaring that
 * a matching Java function is registered; `KrakenProjectConverter` fails the project
 * build if none matches ([hasBody] returns false).
 *
 * The engine indexes functions by `(name, parameter count)` (`FunctionHeader`), hence
 * [arity].
 */
class KrakenFunctionDecl(node: ASTNode) :
    ASTWrapperPsiElement(node),
    PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? = nameLeaf()?.psi

    override fun getName(): String? = nameLeaf()?.text

    override fun setName(name: String): PsiElement {
        val leaf = nameLeaf()
        if (leaf is LeafElement) leaf.replaceWithText(name)
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    /** Declared parameter count: together with the name, the function's identity. */
    val arity: Int
        get() = node.findChildByType(KrakenTypes.FUNCTION_PARAMS)
            ?.getChildren(null)
            ?.count { it.elementType == KrakenTypes.FUNCTION_PARAM }
            ?: 0

    val parameters: List<String>
        get() = node.findChildByType(KrakenTypes.FUNCTION_PARAMS)
            ?.getChildren(null)
            ?.filter { it.elementType == KrakenTypes.FUNCTION_PARAM }
            ?.map { it.text.trim() }
            .orEmpty()

    val returnType: String?
        get() = node.findChildByType(KrakenTypes.RETURN_TYPE)
            ?.findChildByType(KrakenTypes.TYPE_REF)
            ?.text
            ?.trim()

    /** A `T is Number` bound from `Function <…> Name(…)`. */
    data class GenericBound(
        val generic: String,
        val bound: String?,
        val nameElement: PsiElement,
        val boundElement: PsiElement?,
    )

    /** A `Coverage[] coverages` parameter; the name is optional when parsing. */
    data class Parameter(
        val type: String?,
        val name: String?,
        val typeElement: PsiElement?,
        val nameElement: PsiElement?,
    )

    /**
     * Declared generic bounds, in source order.
     *
     * In `generic_bound ::= id IS_KW type_ref`, `id` is private in the BNF, so the generic
     * name is the node's first leaf and the bound its only `TYPE_REF`.
     */
    val genericBounds: List<GenericBound>
        get() = node.findChildByType(KrakenTypes.GENERIC_BOUNDS)
            ?.getChildren(null)
            ?.filter { it.elementType == KrakenTypes.GENERIC_BOUND }
            ?.mapNotNull { bound ->
                val name = firstMeaningfulChild(bound) ?: return@mapNotNull null
                val type = bound.findChildByType(KrakenTypes.TYPE_REF)
                GenericBound(name.text.trim(), type?.text?.trim(), name.psi, type?.psi)
            }
            .orEmpty()

    val parameterList: List<Parameter>
        get() = node.findChildByType(KrakenTypes.FUNCTION_PARAMS)
            ?.getChildren(null)
            ?.filter { it.elementType == KrakenTypes.FUNCTION_PARAM }
            ?.map { param ->
                val type = param.findChildByType(KrakenTypes.TYPE_REF)
                val name = type?.let { firstMeaningfulChild(param, after = it) }
                Parameter(type?.text?.trim(), name?.text?.trim(), type?.psi, name?.psi)
            }
            .orEmpty()

    /** The `TYPE_REF` of the `: Type` clause, to anchor diagnostics on. */
    val returnTypeElement: PsiElement?
        get() = node.findChildByType(KrakenTypes.RETURN_TYPE)
            ?.findChildByType(KrakenTypes.TYPE_REF)
            ?.psi

    /** False for a bare signature, whose implementation is in Java. */
    fun hasBody(): Boolean = node.findChildByType(KrakenTypes.FUNCTION_BODY) != null

    /** `Limits(Coverage[] coverages) : Number[]` */
    fun signature(): String {
        val head = "${name.orEmpty()}(${parameters.joinToString(", ")})"
        return returnType?.let { "$head : $it" } ?: head
    }

    /** Doc comment immediately preceding the declaration, if any. */
    fun docComment(): PsiElement? = PsiTreeUtil.skipWhitespacesBackward(this)
        ?.takeIf { it.node.elementType == KrakenTypes.DOC_COMMENT }

    override fun getPresentation(): ItemPresentation = KrakenPresentations.of(
        this,
        signature(),
        KrakenPresentations.FUNCTION_ICON,
    )

    /**
     * The name is the last token before the opening parenthesis: `id` is private in the
     * BNF, so it has no node, and generic bounds (`Function <T is X> Name(…)`) come before
     * it.
     */
    private fun nameLeaf(): ASTNode? {
        val paren = node.findChildByType(KrakenTypes.LPAREN) ?: return null
        var candidate = paren.treePrev
        while (candidate != null && candidate.psi is com.intellij.psi.PsiWhiteSpace) {
            candidate = candidate.treePrev
        }
        return candidate?.takeIf { it.elementType != KrakenTypes.GENERIC_BOUNDS }
    }

    /**
     * First meaningful child of [parent], optionally after [after].
     *
     * Type sub-rules are private in the BNF, so `id` produces no node and the name is a
     * sibling token of `TYPE_REF` rather than a subtree.
     */
    private fun firstMeaningfulChild(parent: ASTNode, after: ASTNode? = null): ASTNode? {
        var child = after?.treeNext ?: parent.firstChildNode
        while (child != null) {
            if (child.psi !is com.intellij.psi.PsiWhiteSpace &&
                child.psi !is com.intellij.psi.PsiComment &&
                child.textLength > 0
            ) {
                return child
            }
            child = child.treeNext
        }
        return null
    }
}
