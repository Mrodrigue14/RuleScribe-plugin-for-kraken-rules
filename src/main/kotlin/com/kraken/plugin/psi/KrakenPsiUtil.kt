package com.kraken.plugin.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.kraken.plugin.parser.KrakenTypes

/** Reading and editing the tokens of a declaration, where the grammar leaves no dedicated PSI. */
object KrakenPsiUtil {

    /** Identifier tokens among [node]'s children, up to [stop] excluded. */
    fun identifiersBefore(node: ASTNode, stop: IElementType): List<String> = generateSequence(node.firstChildNode) { it.treeNext }
        .takeWhile { it.elementType != stop }
        .filter { it.elementType in ID_TOKENS }
        .map { it.text }
        .toList()

    /** First identifier token among [node]'s children after [keyword]. */
    fun firstIdAfter(node: ASTNode, keyword: IElementType): ASTNode? = firstIdFrom(node.findChildByType(keyword)?.treeNext)

    /** First identifier token among [node]'s children after the first of [keywords]. */
    fun firstIdAfter(node: ASTNode, keywords: TokenSet): ASTNode? = firstIdFrom(node.findChildByType(keywords)?.treeNext)

    private fun firstIdFrom(start: ASTNode?): ASTNode? = generateSequence(start) { it.treeNext }.firstOrNull { it.elementType in ID_TOKENS }

    /** AST children of [element], leaves included, without whitespace and comments. */
    fun significantChildren(element: PsiElement): List<PsiElement> = element.node.getChildren(null)
        .map { it.psi }
        .filter { it !is PsiWhiteSpace && it !is PsiComment }

    /** Replaces a string's content, keeping its original quote character. */
    fun replaceQuoted(leaf: ASTNode?, content: String) {
        if (leaf !is LeafElement) return
        val quote = leaf.text.firstOrNull() ?: '"'
        leaf.replaceWithText("$quote$content$quote")
    }

    fun insideQuotes(start: Int, length: Int): TextRange = if (length >= 2) TextRange(start + 1, start + length - 1) else TextRange(start, start + length)

    /** Tokens accepted as identifiers (mirrors the BNF `id` rule). */
    @JvmField
    val ID_TOKENS: TokenSet = TokenSet.create(
        KrakenTypes.IDENTIFIER,
        KrakenTypes.ON_KW, KrakenTypes.FROM_KW, KrakenTypes.TO_KW,
        KrakenTypes.MIN_KW, KrakenTypes.MAX_KW, KrakenTypes.STEP_KW,
        KrakenTypes.SIZE_KW, KrakenTypes.LENGTH_KW, KrakenTypes.NUMBER_KW,
        KrakenTypes.EMPTY_KW, KrakenTypes.MANDATORY_KW, KrakenTypes.DISABLED_KW,
        KrakenTypes.HIDDEN_KW, KrakenTypes.DESCRIPTION_KW, KrakenTypes.PRIORITY_KW,
        KrakenTypes.EXTERNAL_KW, KrakenTypes.CHILD_KW, KrakenTypes.ROOT_KW,
        KrakenTypes.SYSTEM_KW, KrakenTypes.CONTEXT_KW, KrakenTypes.DIMENSION_KW,
        KrakenTypes.FUNCTION_KW, KrakenTypes.MATCHES_KW, KrakenTypes.INCLUDE_KW,
        KrakenTypes.NAMESPACE_KW, KrakenTypes.ERROR_KW, KrakenTypes.WARN_KW,
        KrakenTypes.INFO_KW,
    )
}
