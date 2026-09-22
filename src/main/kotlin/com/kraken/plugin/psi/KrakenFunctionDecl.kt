package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
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

    val parameters: List<KrakenFunctionParam>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(parameterListElement, KrakenFunctionParam::class.java)

    val arity: Int
        get() = parameters.size

    val returnType: String?
        get() = returnTypeElement?.text?.trim()

    /** A `T is Number` bound from `Function <…> Name(…)`. */
    data class GenericBound(
        val generic: String,
        val bound: String?,
        val nameElement: PsiElement,
        val boundElement: PsiElement?,
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

    fun parameterNamed(name: String): KrakenFunctionParam? = parameters.firstOrNull { it.name == name }

    private val parameterListElement: PsiElement?
        get() = node.findChildByType(KrakenTypes.FUNCTION_PARAMS)?.psi

    /** The `TYPE_REF` of the `: Type` clause, to anchor diagnostics on. */
    val returnTypeElement: PsiElement?
        get() = node.findChildByType(KrakenTypes.RETURN_TYPE)
            ?.findChildByType(KrakenTypes.TYPE_REF)
            ?.psi

    fun hasBody(): Boolean = node.findChildByType(KrakenTypes.FUNCTION_BODY) != null

    /** `Coverage[] coverages, Number n` */
    fun parameterText(): String = parameters.joinToString(", ") { it.text.trim() }

    /** `Limits(Coverage[] coverages) : Number[]` */
    fun signature(): String {
        val head = "${name.orEmpty()}(${parameterText()})"
        return returnType?.let { "$head : $it" } ?: head
    }

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
        while (candidate != null && candidate.psi is PsiWhiteSpace) {
            candidate = candidate.treePrev
        }
        return candidate?.takeIf { it.elementType != KrakenTypes.GENERIC_BOUNDS }
    }
}

/** A `Coverage[] coverages` parameter; the name is optional when parsing. */
class KrakenFunctionParam(node: ASTNode) : ASTWrapperPsiElement(node) {

    val typeElement: PsiElement?
        get() = node.findChildByType(KrakenTypes.TYPE_REF)?.psi

    val typeName: String?
        get() = typeElement?.text?.trim()

    /** The name is a sibling token of `TYPE_REF`: `id` is private in the BNF. */
    val nameElement: PsiElement?
        get() = typeElement?.let { firstMeaningfulChild(node, after = it.node) }?.psi

    override fun getName(): String? = nameElement?.text?.trim()
}

/**
 * First child of [parent] that is neither whitespace, a comment nor empty, optionally
 * after [after]. Type sub-rules and `id` are private in the BNF, so names are sibling
 * tokens rather than subtrees.
 */
private fun firstMeaningfulChild(parent: ASTNode, after: ASTNode? = null): ASTNode? = generateSequence(after?.treeNext ?: parent.firstChildNode) { it.treeNext }
    .firstOrNull { it.psi !is PsiWhiteSpace && it.psi !is PsiComment && it.textLength > 0 }
