package com.kraken.plugin.formatter

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock
import com.kraken.plugin.lang.KrakenParserDefinition
import com.kraken.plugin.parser.KrakenTypes

class KrakenFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val rootBlock = KrakenBlock(formattingContext.node, null, null, Indent.getNoneIndent())
        return FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            rootBlock,
            formattingContext.codeStyleSettings,
        )
    }
}

class KrakenBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    private val indent: Indent,
) : AbstractBlock(node, wrap, alignment) {

    override fun getIndent(): Indent = indent

    override fun buildChildren(): List<Block> {
        // Les expressions KEL n'ont pas encore de règles de mise en page :
        // on les traite comme des blocs opaques pour que le formateur (et le
        // "Reformat on paste" de l'IDE) préserve l'indentation manuelle de
        // leurs lignes de continuation au lieu de les aplatir.
        if (myNode.elementType == KrakenTypes.EXPRESSION) return emptyList()
        val blocks = mutableListOf<Block>()
        var child = myNode.firstChildNode
        var seenLBrace = false
        while (child != null) {
            if (child.elementType == KrakenTypes.LBRACE) seenLBrace = true
            if (child.elementType == KrakenTypes.RBRACE) seenLBrace = false
            if (child.elementType != TokenType.WHITE_SPACE && child.textLength > 0) {
                blocks.add(KrakenBlock(child, null, null, childIndent(child, seenLBrace)))
            }
            child = child.treeNext
        }
        return blocks
    }

    private fun childIndent(child: ASTNode, afterLBrace: Boolean): Indent {
        val type = child.elementType
        if (type == KrakenTypes.LBRACE || type == KrakenTypes.RBRACE) return Indent.getNoneIndent()
        return if (myNode.elementType in KrakenParserDefinition.BRACE_BLOCKS && afterLBrace) {
            Indent.getNormalIndent()
        } else {
            Indent.getNoneIndent()
        }
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun isLeaf(): Boolean = myNode.firstChildNode == null || myNode.elementType == KrakenTypes.EXPRESSION

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = if (myNode.elementType in KrakenParserDefinition.BRACE_BLOCKS) {
        ChildAttributes(Indent.getNormalIndent(), null)
    } else {
        ChildAttributes(Indent.getNoneIndent(), null)
    }
}
