package com.kraken.plugin.parser

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/**
 * Hand-written lexer for the Kraken `.rules` DSL.
 *
 * Keywords are case-insensitive, like the official ANTLR grammar (Common.g4), which
 * accepts "rule", "Rule" and "RULE".
 */
class KrakenLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var currentToken: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = currentToken
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= bufferEnd) {
            currentToken = null
            return
        }
        val c = buffer[tokenStart]
        when {
            c.isWhitespace() -> {
                tokenEnd = scanWhile(tokenStart + 1) { it.isWhitespace() }
                currentToken = TokenType.WHITE_SPACE
            }

            c == '/' && peek(1) == '/' -> {
                tokenEnd = scanWhile(tokenStart + 2) { it != '\n' && it != '\r' }
                currentToken = KrakenTypes.LINE_COMMENT
            }

            c == '/' && peek(1) == '*' -> scanBlockComment()

            c == '"' || c == '\'' -> scanString(c)

            c.isDigit() -> scanNumber()

            c.isLetter() || c == '_' -> scanWord()

            else -> scanSymbol(c)
        }
    }

    private fun token(type: IElementType, length: Int) {
        tokenEnd = tokenStart + length
        currentToken = type
    }

    private fun peek(offset: Int): Char {
        val index = tokenStart + offset
        return if (index < bufferEnd) buffer[index] else '\u0000'
    }

    private inline fun scanWhile(from: Int, predicate: (Char) -> Boolean): Int {
        var i = from
        while (i < bufferEnd && predicate(buffer[i])) i++
        return i
    }

    private fun scanBlockComment() {
        // "/**" starts a doc comment, except in the empty comment "/**/".
        val isDoc = peek(2) == '*' && peek(3) != '/'
        var i = tokenStart + if (isDoc) 3 else 2
        var closed = false
        while (i < bufferEnd) {
            if (buffer[i] == '*' && i + 1 < bufferEnd && buffer[i + 1] == '/') {
                i += 2
                closed = true
                break
            }
            i++
        }
        tokenEnd = if (closed) i else bufferEnd
        currentToken = if (isDoc) KrakenTypes.DOC_COMMENT else KrakenTypes.BLOCK_COMMENT
    }

    private fun scanString(quote: Char) {
        var i = tokenStart + 1
        while (i < bufferEnd) {
            val ch = buffer[i]
            if (ch == '\\' && i + 1 < bufferEnd) {
                i += 2
                continue
            }
            if (ch == quote) {
                i++
                break
            }
            // As in the official ANTLR grammar, a string may span several lines (template
            // messages with '${...}').
            i++
        }
        tokenEnd = i
        currentToken = KrakenTypes.STRING
    }

    private fun scanNumber() {
        // KEL date and datetime literals: 2020-01-01 or 2020-01-01T10:00:00Z
        val dateMatch = DATE_TIME_REGEX.matchAt(buffer, tokenStart)
        if (dateMatch != null) {
            tokenEnd = dateMatch.range.last + 1
            currentToken = KrakenTypes.NUMBER_LIT
            return
        }
        var i = scanWhile(tokenStart + 1) { it.isDigit() }
        if (i < bufferEnd && buffer[i] == '.' && i + 1 < bufferEnd && buffer[i + 1].isDigit()) {
            i = scanWhile(i + 1) { it.isDigit() }
        }
        tokenEnd = i
        currentToken = KrakenTypes.NUMBER_LIT
    }

    private fun scanWord() {
        val end = scanWhile(tokenStart + 1) { it.isLetterOrDigit() || it == '_' }
        tokenEnd = end
        val word = buffer.subSequence(tokenStart, end).toString().lowercase()
        currentToken = KEYWORDS[word] ?: KrakenTypes.IDENTIFIER
    }

    /**
     * Longest match first, so `>=` and `||` are one operator rather than `>` then `=` or two
     * PIPEs. Only real operators are recognised, rather than any run of operator
     * characters, which would accept `a &|&~ b` as one OP: a typo fails at the right offset.
     */
    private fun scanSymbol(c: Char) {
        val pair = TWO_CHAR_TOKENS["$c${peek(1)}"]
        when {
            pair != null -> token(pair, 2)
            c in SINGLE_CHAR_TOKENS -> token(SINGLE_CHAR_TOKENS.getValue(c), 1)
            c in SINGLE_CHAR_OPERATORS -> token(KrakenTypes.OP, 1)
            else -> token(TokenType.BAD_CHARACTER, 1)
        }
    }

    companion object {
        /** The operators of `Common.g4` and nothing else: `^`, `~` and a lone `&` are not operators. */
        private val TWO_CHAR_TOKENS: Map<String, IElementType> = mapOf(
            "?." to KrakenTypes.QDOT,
            "?[" to KrakenTypes.QLBRACKET,
            "**" to KrakenTypes.OP,
            ">=" to KrakenTypes.OP,
            "<=" to KrakenTypes.OP,
            "||" to KrakenTypes.OP,
            "!=" to KrakenTypes.OP,
            "==" to KrakenTypes.OP,
            "&&" to KrakenTypes.OP,
        )
        private const val SINGLE_CHAR_OPERATORS = "+-=!?%"

        private val DATE_TIME_REGEX =
            Regex("""\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}:\d{2}Z?)?""")

        private val SINGLE_CHAR_TOKENS: Map<Char, IElementType> = mapOf(
            '{' to KrakenTypes.LBRACE,
            '}' to KrakenTypes.RBRACE,
            '(' to KrakenTypes.LPAREN,
            ')' to KrakenTypes.RPAREN,
            '[' to KrakenTypes.LBRACKET,
            ']' to KrakenTypes.RBRACKET,
            ',' to KrakenTypes.COMMA,
            '.' to KrakenTypes.DOT,
            ':' to KrakenTypes.COLON,
            '@' to KrakenTypes.AT,
            '*' to KrakenTypes.STAR,
            '<' to KrakenTypes.LT,
            '>' to KrakenTypes.GT,
            '/' to KrakenTypes.OP,
            // A lone `|` is its own token because it also separates union type members.
            '|' to KrakenTypes.PIPE,
        )

        private val KEYWORDS: Map<String, IElementType> = mapOf(
            "namespace" to KrakenTypes.NAMESPACE_KW,
            "include" to KrakenTypes.INCLUDE_KW,
            "import" to KrakenTypes.IMPORT_KW,
            "from" to KrakenTypes.FROM_KW,
            "rule" to KrakenTypes.RULE_KW,
            "rules" to KrakenTypes.RULES_KW,
            "on" to KrakenTypes.ON_KW,
            "context" to KrakenTypes.CONTEXT_KW,
            "contexts" to KrakenTypes.CONTEXTS_KW,
            "system" to KrakenTypes.SYSTEM_KW,
            "root" to KrakenTypes.ROOT_KW,
            "external" to KrakenTypes.EXTERNAL_KW,
            "externalcontext" to KrakenTypes.EXTERNAL_CONTEXT_KW,
            "externalentity" to KrakenTypes.EXTERNAL_ENTITY_KW,
            "child" to KrakenTypes.CHILD_KW,
            "is" to KrakenTypes.IS_KW,
            "entrypoint" to KrakenTypes.ENTRYPOINT_KW,
            "entrypoints" to KrakenTypes.ENTRYPOINTS_KW,
            "when" to KrakenTypes.WHEN_KW,
            "assert" to KrakenTypes.ASSERT_KW,
            "set" to KrakenTypes.SET_KW,
            "default" to KrakenTypes.DEFAULT_KW,
            "reset" to KrakenTypes.RESET_KW,
            "to" to KrakenTypes.TO_KW,
            "mandatory" to KrakenTypes.MANDATORY_KW,
            "empty" to KrakenTypes.EMPTY_KW,
            "disabled" to KrakenTypes.DISABLED_KW,
            "hidden" to KrakenTypes.HIDDEN_KW,
            "matches" to KrakenTypes.MATCHES_KW,
            "size" to KrakenTypes.SIZE_KW,
            "min" to KrakenTypes.MIN_KW,
            "max" to KrakenTypes.MAX_KW,
            "length" to KrakenTypes.LENGTH_KW,
            "number" to KrakenTypes.NUMBER_KW,
            "step" to KrakenTypes.STEP_KW,
            "in" to KrakenTypes.IN_KW,
            "overridable" to KrakenTypes.OVERRIDABLE_KW,
            "error" to KrakenTypes.ERROR_KW,
            "warn" to KrakenTypes.WARN_KW,
            "info" to KrakenTypes.INFO_KW,
            "dimension" to KrakenTypes.DIMENSION_KW,
            "function" to KrakenTypes.FUNCTION_KW,
            "priority" to KrakenTypes.PRIORITY_KW,
            "description" to KrakenTypes.DESCRIPTION_KW,
            "notstrict" to KrakenTypes.NOT_STRICT_KW,
            "forbidtarget" to KrakenTypes.FORBID_TARGET_KW,
            "forbidreference" to KrakenTypes.FORBID_REFERENCE_KW,
            "serversideonly" to KrakenTypes.SERVER_SIDE_ONLY_KW,
            "true" to KrakenTypes.TRUE_KW,
            "false" to KrakenTypes.FALSE_KW,
            "null" to KrakenTypes.NULL_KW,
            "and" to KrakenTypes.AND_KW,
            "or" to KrakenTypes.OR_KW,
            "not" to KrakenTypes.NOT_KW,
            "if" to KrakenTypes.IF_KW,
            "then" to KrakenTypes.THEN_KW,
            "else" to KrakenTypes.ELSE_KW,
            "for" to KrakenTypes.FOR_KW,
            "every" to KrakenTypes.EVERY_KW,
            "some" to KrakenTypes.SOME_KW,
            "return" to KrakenTypes.RETURN_KW,
            "this" to KrakenTypes.THIS_KW,
            "instanceof" to KrakenTypes.INSTANCEOF_KW,
            "typeof" to KrakenTypes.TYPEOF_KW,
            "satisfies" to KrakenTypes.SATISFIES_KW,
        )

        /** Every keyword token, for features that treat keywords alike. */
        val KEYWORD_TOKENS: TokenSet = TokenSet.create(*KEYWORDS.values.toTypedArray())
    }
}
