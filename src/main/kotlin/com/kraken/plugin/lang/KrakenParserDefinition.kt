package com.kraken.plugin.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.tree.TokenSet
import com.kraken.plugin.parser.KrakenLexer
import com.kraken.plugin.parser.KrakenParser
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenDimensionDecl
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.psi.KrakenRuleRef

class KrakenParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = KrakenLexer()

    override fun createParser(project: Project?): PsiParser = KrakenParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        KrakenTypes.RULE_DECL -> KrakenRuleDecl(node)
        KrakenTypes.ENTRY_POINT_DECL -> KrakenEntryPointDecl(node)
        KrakenTypes.RULE_REF -> KrakenRuleRef(node)
        KrakenTypes.EP_REF -> KrakenEpRef(node)
        KrakenTypes.DIMENSION_DECL -> KrakenDimensionDecl(node)
        else -> ASTWrapperPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = KrakenFile(viewProvider)

    companion object {
        @JvmField
        val FILE: IFileElementType = object : IStubFileElementType<PsiFileStub<KrakenFile>>(
            "kraken.FILE", KrakenLanguage
        ) {
            // À incrémenter à chaque évolution de la grammaire ou des stubs
            override fun getStubVersion(): Int = 2
            override fun getExternalId(): String = "kraken.FILE"
        }

        @JvmField
        val WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

        @JvmField
        val COMMENTS: TokenSet = TokenSet.create(
            KrakenTypes.LINE_COMMENT,
            KrakenTypes.BLOCK_COMMENT,
            KrakenTypes.DOC_COMMENT
        )

        @JvmField
        val STRINGS: TokenSet = TokenSet.create(KrakenTypes.STRING)
    }
}
