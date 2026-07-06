package com.kraken.plugin.highlighter

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.kraken.plugin.parser.KrakenLexer
import com.kraken.plugin.parser.KrakenTypes

class KrakenSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = KrakenLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        pack(ATTRIBUTES[tokenType])

    companion object {
        val KEYWORD: TextAttributesKey =
            createTextAttributesKey("KRAKEN_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val STRING: TextAttributesKey =
            createTextAttributesKey("KRAKEN_STRING", DefaultLanguageHighlighterColors.STRING)
        val NUMBER: TextAttributesKey =
            createTextAttributesKey("KRAKEN_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val LINE_COMMENT: TextAttributesKey =
            createTextAttributesKey("KRAKEN_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val BLOCK_COMMENT: TextAttributesKey =
            createTextAttributesKey("KRAKEN_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        val DOC_COMMENT: TextAttributesKey =
            createTextAttributesKey("KRAKEN_DOC_COMMENT", DefaultLanguageHighlighterColors.DOC_COMMENT)
        val ANNOTATION: TextAttributesKey =
            createTextAttributesKey("KRAKEN_ANNOTATION", DefaultLanguageHighlighterColors.METADATA)
        val IDENTIFIER: TextAttributesKey =
            createTextAttributesKey("KRAKEN_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val OPERATOR: TextAttributesKey =
            createTextAttributesKey("KRAKEN_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val BRACES: TextAttributesKey =
            createTextAttributesKey("KRAKEN_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val PARENTHESES: TextAttributesKey =
            createTextAttributesKey("KRAKEN_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACKETS: TextAttributesKey =
            createTextAttributesKey("KRAKEN_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val COMMA: TextAttributesKey =
            createTextAttributesKey("KRAKEN_COMMA", DefaultLanguageHighlighterColors.COMMA)
        val DOT: TextAttributesKey =
            createTextAttributesKey("KRAKEN_DOT", DefaultLanguageHighlighterColors.DOT)
        val BAD_CHARACTER: TextAttributesKey =
            createTextAttributesKey("KRAKEN_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val ATTRIBUTES: Map<IElementType, TextAttributesKey> by lazy {
            val map = HashMap<IElementType, TextAttributesKey>()
            for (keyword in listOf(
                KrakenTypes.NAMESPACE_KW, KrakenTypes.INCLUDE_KW, KrakenTypes.IMPORT_KW,
                KrakenTypes.FROM_KW, KrakenTypes.RULE_KW, KrakenTypes.RULES_KW,
                KrakenTypes.ON_KW, KrakenTypes.CONTEXT_KW, KrakenTypes.CONTEXTS_KW,
                KrakenTypes.SYSTEM_KW, KrakenTypes.ROOT_KW, KrakenTypes.EXTERNAL_KW,
                KrakenTypes.EXTERNAL_CONTEXT_KW, KrakenTypes.EXTERNAL_ENTITY_KW,
                KrakenTypes.CHILD_KW, KrakenTypes.IS_KW, KrakenTypes.ENTRYPOINT_KW,
                KrakenTypes.ENTRYPOINTS_KW, KrakenTypes.WHEN_KW, KrakenTypes.ASSERT_KW,
                KrakenTypes.SET_KW, KrakenTypes.DEFAULT_KW, KrakenTypes.RESET_KW,
                KrakenTypes.TO_KW, KrakenTypes.MANDATORY_KW, KrakenTypes.EMPTY_KW,
                KrakenTypes.DISABLED_KW, KrakenTypes.HIDDEN_KW, KrakenTypes.MATCHES_KW,
                KrakenTypes.SIZE_KW, KrakenTypes.MIN_KW, KrakenTypes.MAX_KW,
                KrakenTypes.LENGTH_KW, KrakenTypes.NUMBER_KW, KrakenTypes.STEP_KW,
                KrakenTypes.IN_KW, KrakenTypes.OVERRIDABLE_KW, KrakenTypes.ERROR_KW,
                KrakenTypes.WARN_KW, KrakenTypes.INFO_KW, KrakenTypes.DIMENSION_KW,
                KrakenTypes.FUNCTION_KW, KrakenTypes.PRIORITY_KW, KrakenTypes.DESCRIPTION_KW,
                KrakenTypes.NOT_STRICT_KW, KrakenTypes.FORBID_TARGET_KW,
                KrakenTypes.FORBID_REFERENCE_KW, KrakenTypes.SERVER_SIDE_ONLY_KW,
                KrakenTypes.TRUE_KW, KrakenTypes.FALSE_KW, KrakenTypes.NULL_KW,
                KrakenTypes.AND_KW, KrakenTypes.OR_KW, KrakenTypes.NOT_KW,
                KrakenTypes.IF_KW, KrakenTypes.THEN_KW, KrakenTypes.ELSE_KW,
                KrakenTypes.FOR_KW, KrakenTypes.EVERY_KW, KrakenTypes.SOME_KW,
                KrakenTypes.RETURN_KW, KrakenTypes.THIS_KW, KrakenTypes.INSTANCEOF_KW,
                KrakenTypes.TYPEOF_KW, KrakenTypes.SATISFIES_KW
            )) {
                map[keyword] = KEYWORD
            }
            map[KrakenTypes.STRING] = STRING
            map[KrakenTypes.NUMBER_LIT] = NUMBER
            map[KrakenTypes.LINE_COMMENT] = LINE_COMMENT
            map[KrakenTypes.BLOCK_COMMENT] = BLOCK_COMMENT
            map[KrakenTypes.DOC_COMMENT] = DOC_COMMENT
            map[KrakenTypes.AT] = ANNOTATION
            map[KrakenTypes.IDENTIFIER] = IDENTIFIER
            map[KrakenTypes.OP] = OPERATOR
            map[KrakenTypes.LT] = OPERATOR
            map[KrakenTypes.GT] = OPERATOR
            map[KrakenTypes.STAR] = OPERATOR
            map[KrakenTypes.LBRACE] = BRACES
            map[KrakenTypes.RBRACE] = BRACES
            map[KrakenTypes.LPAREN] = PARENTHESES
            map[KrakenTypes.RPAREN] = PARENTHESES
            map[KrakenTypes.LBRACKET] = BRACKETS
            map[KrakenTypes.RBRACKET] = BRACKETS
            map[KrakenTypes.COMMA] = COMMA
            map[KrakenTypes.DOT] = DOT
            map[KrakenTypes.QDOT] = DOT
            map[KrakenTypes.QLBRACKET] = BRACKETS
            map[TokenType.BAD_CHARACTER] = BAD_CHARACTER
            map
        }
    }
}
