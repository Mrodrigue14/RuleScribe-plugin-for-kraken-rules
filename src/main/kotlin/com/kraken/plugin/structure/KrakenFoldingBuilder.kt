package com.kraken.plugin.structure

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes

class KrakenFoldingBuilder :
    FoldingBuilderEx(),
    DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        PsiTreeUtil.processElements(root) { element ->
            val node = element.node
            if (node != null && node.elementType in FOLDABLE) {
                val lbrace = node.findChildByType(KrakenTypes.LBRACE)
                val rbrace = lastChildOfType(node, KrakenTypes.RBRACE)
                if (lbrace != null && rbrace != null && rbrace.startOffset > lbrace.startOffset + 1) {
                    val range = TextRange(lbrace.startOffset, rbrace.textRange.endOffset)
                    if (document.getLineNumber(range.startOffset) < document.getLineNumber(range.endOffset - 1)) {
                        descriptors.add(FoldingDescriptor(node, range))
                    }
                }
            }
            true
        }
        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = "{…}"

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    private fun lastChildOfType(node: ASTNode, type: com.intellij.psi.tree.IElementType): ASTNode? {
        var child = node.lastChildNode
        while (child != null) {
            if (child.elementType == type) return child
            child = child.treePrev
        }
        return null
    }

    companion object {
        private val FOLDABLE = TokenSet.create(
            KrakenTypes.RULE_BODY,
            KrakenTypes.CONTEXT_DECL,
            KrakenTypes.CONTEXTS_BLOCK,
            KrakenTypes.RULES_BLOCK,
            KrakenTypes.ENTRY_POINT_DECL,
            KrakenTypes.ENTRY_POINTS_BLOCK,
            KrakenTypes.EXTERNAL_CONTEXT_DECL,
            KrakenTypes.EXTERNAL_ENTITY_DECL,
            KrakenTypes.FUNCTION_BODY,
        )
    }
}
