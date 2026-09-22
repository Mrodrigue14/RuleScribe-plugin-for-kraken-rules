package com.kraken.plugin.structure

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenParserDefinition
import com.kraken.plugin.parser.KrakenTypes

class KrakenFoldingBuilder :
    FoldingBuilderEx(),
    DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> = PsiTreeUtil.collectElements(root) { it.node?.elementType in KrakenParserDefinition.BRACE_BLOCKS }
        .mapNotNull { block -> braceRange(block.node, document)?.let { FoldingDescriptor(block.node, it) } }
        .toTypedArray()

    /** From `{` to `}`, when the block is not empty and spans several lines. */
    private fun braceRange(block: ASTNode, document: Document): TextRange? {
        val lbrace = block.findChildByType(KrakenTypes.LBRACE) ?: return null
        val rbrace = TreeUtil.findChildBackward(block, KrakenTypes.RBRACE) ?: return null
        if (rbrace.startOffset <= lbrace.startOffset + 1) return null
        val range = TextRange(lbrace.startOffset, rbrace.textRange.endOffset)
        return range.takeIf { document.getLineNumber(it.startOffset) < document.getLineNumber(it.endOffset - 1) }
    }

    override fun getPlaceholderText(node: ASTNode): String = "{…}"

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
