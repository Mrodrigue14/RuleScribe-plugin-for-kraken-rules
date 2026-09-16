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

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = pack(ATTRIBUTES[tokenType])

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

        // A called name and a variable are both IDENTIFIER, so calls cannot be told apart
        // lexically: KrakenFunctionAnnotator sets these two keys from the PSI.
        val NATIVE_FUNCTION: TextAttributesKey =
            createTextAttributesKey(
                "KRAKEN_NATIVE_FUNCTION",
                DefaultLanguageHighlighterColors.STATIC_METHOD,
            )
        val DECLARED_FUNCTION: TextAttributesKey =
            createTextAttributesKey(
                "KRAKEN_DECLARED_FUNCTION",
                DefaultLanguageHighlighterColors.FUNCTION_CALL,
            )

        // Set by KrakenReferenceAnnotator, only on names that actually resolve.
        val CONTEXT_REFERENCE: TextAttributesKey =
            createTextAttributesKey(
                "KRAKEN_CONTEXT_REFERENCE",
                DefaultLanguageHighlighterColors.CLASS_REFERENCE,
            )
        val FIELD_REFERENCE: TextAttributesKey =
            createTextAttributesKey(
                "KRAKEN_FIELD_REFERENCE",
                DefaultLanguageHighlighterColors.INSTANCE_FIELD,
            )
        val BRACES: TextAttributesKey =
            createTextAttributesKey("KRAKEN_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val PARENTHESES: TextAttributesKey =
            createTextAttributesKey("KRAKEN_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACKETS: TextAttributesKey =
            createTextAttributesKey("KRAKEN_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)

        /**
         * Nesting depth of braces, parentheses and brackets. Three colours repeat; beyond
         * that, levels cannot be told apart by eye anyway.
         *
         * Red is excluded and reserved for [UNMATCHED_BRACKET]: a colour that means "error"
         * cannot also be a depth colour.
         */
        val BRACKET_DEPTH: List<TextAttributesKey> = listOf(
            createTextAttributesKey("KRAKEN_BRACKET_DEPTH_1"),
            createTextAttributesKey("KRAKEN_BRACKET_DEPTH_2"),
            createTextAttributesKey("KRAKEN_BRACKET_DEPTH_3"),
        )

        /** Brace without a partner, or whose match is ambiguous. */
        val UNMATCHED_BRACKET: TextAttributesKey =
            createTextAttributesKey("KRAKEN_UNMATCHED_BRACKET")

        val COMMA: TextAttributesKey =
            createTextAttributesKey("KRAKEN_COMMA", DefaultLanguageHighlighterColors.COMMA)
        val DOT: TextAttributesKey =
            createTextAttributesKey("KRAKEN_DOT", DefaultLanguageHighlighterColors.DOT)
        val BAD_CHARACTER: TextAttributesKey =
            createTextAttributesKey("KRAKEN_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val ATTRIBUTES: Map<IElementType, TextAttributesKey> by lazy {
            val map = HashMap<IElementType, TextAttributesKey>()
            SyntaxHighlighterBase.fillMap(map, KrakenLexer.KEYWORD_TOKENS, KEYWORD)
            map[KrakenTypes.STRING] = STRING
            map[KrakenTypes.NUMBER_LIT] = NUMBER
            map[KrakenTypes.LINE_COMMENT] = LINE_COMMENT
            map[KrakenTypes.BLOCK_COMMENT] = BLOCK_COMMENT
            map[KrakenTypes.DOC_COMMENT] = DOC_COMMENT
            map[KrakenTypes.AT] = ANNOTATION
            map[KrakenTypes.IDENTIFIER] = IDENTIFIER
            map[KrakenTypes.OP] = OPERATOR
            map[KrakenTypes.PIPE] = OPERATOR
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
