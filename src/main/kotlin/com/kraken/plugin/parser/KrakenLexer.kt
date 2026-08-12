package com.kraken.plugin.parser

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * Lexer manuscrit pour le DSL Kraken .rules.
 *
 * Les mots-clés sont reconnus sans tenir compte de la casse, en cohérence
 * avec la grammaire ANTLR officielle (Common.g4) qui accepte par exemple
 * "rule", "Rule" et "RULE".
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
            c == '?' && peek(1) == '.' -> twoCharToken(KrakenTypes.QDOT)
            c == '?' && peek(1) == '[' -> twoCharToken(KrakenTypes.QLBRACKET)
            c == '*' && peek(1) == '*' -> twoCharToken(KrakenTypes.OP)
            // Avant SINGLE_CHAR_TOKENS, qui rendrait sinon `<` puis `=`
            // séparément : `>=` est un opérateur à part entière côté moteur
            // (`OP_MORE_EQUALS` dans Common.g4), et le découper empêchait
            // toute analyse de la comparaison.
            (c == '>' || c == '<') && peek(1) == '=' -> twoCharToken(KrakenTypes.OP)
            else -> scanSymbol(c)
        }
    }

    private fun twoCharToken(type: IElementType) {
        tokenEnd = tokenStart + 2
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
        // "/**..." = commentaire de documentation, sauf le cas limite "/**/"
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
            // NB : comme dans la grammaire ANTLR officielle, une chaîne peut
            // s'étendre sur plusieurs lignes (messages template '${...}').
            i++
        }
        tokenEnd = i
        currentToken = KrakenTypes.STRING
    }

    private fun scanNumber() {
        // Littéraux date / datetime du KEL : 2020-01-01 ou 2020-01-01T10:00:00Z
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

    private fun scanSymbol(c: Char) {
        val single = SINGLE_CHAR_TOKENS[c]
        if (single != null) {
            tokenEnd = tokenStart + 1
            currentToken = single
            return
        }
        // Plus long opérateur réel d'abord, plutôt qu'une suite maximale de
        // caractères d'opérateur : `a &|&~ b` se lexait en un seul `OP` que la
        // grammaire acceptait sans broncher. En ne reconnaissant que les
        // opérateurs de Common.g4, une faute de frappe devient une erreur
        // précise, au bon offset.
        if ("$c${peek(1)}" in TWO_CHAR_OPERATORS) {
            twoCharToken(KrakenTypes.OP)
            return
        }
        if (c in SINGLE_CHAR_OPERATORS) {
            tokenEnd = tokenStart + 1
            currentToken = KrakenTypes.OP
            return
        }
        tokenEnd = tokenStart + 1
        currentToken = TokenType.BAD_CHARACTER
    }

    companion object {
        /**
         * Opérateurs de `Common.g4`, et rien d'autre. `^` et `~` n'y figurent
         * pas, `&` seul non plus (seul `&&` existe) : les accepter revenait à
         * laisser passer n'importe quelle suite de symboles.
         *
         * `**`, `?.`, `?[`, `>=` et `<=` sont reconnus en amont, avant la
         * table des tokens à un caractère.
         */
        private val TWO_CHAR_OPERATORS = setOf("!=", "==", "&&", "||")
        private const val SINGLE_CHAR_OPERATORS = "+-=!?|%"

        private val DATE_TIME_REGEX =
            Regex("""\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}:\d{2}Z?)?""")

        private val SINGLE_CHAR_TOKENS: Map<Char, IElementType> by lazy {
            mapOf(
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
                '/' to KrakenTypes.OP
            )
        }

        private val KEYWORDS: Map<String, IElementType> by lazy {
            val map = HashMap<String, IElementType>()
            map["namespace"] = KrakenTypes.NAMESPACE_KW
            map["include"] = KrakenTypes.INCLUDE_KW
            map["import"] = KrakenTypes.IMPORT_KW
            map["from"] = KrakenTypes.FROM_KW
            map["rule"] = KrakenTypes.RULE_KW
            map["rules"] = KrakenTypes.RULES_KW
            map["on"] = KrakenTypes.ON_KW
            map["context"] = KrakenTypes.CONTEXT_KW
            map["contexts"] = KrakenTypes.CONTEXTS_KW
            map["system"] = KrakenTypes.SYSTEM_KW
            map["root"] = KrakenTypes.ROOT_KW
            map["external"] = KrakenTypes.EXTERNAL_KW
            map["externalcontext"] = KrakenTypes.EXTERNAL_CONTEXT_KW
            map["externalentity"] = KrakenTypes.EXTERNAL_ENTITY_KW
            map["child"] = KrakenTypes.CHILD_KW
            map["is"] = KrakenTypes.IS_KW
            map["entrypoint"] = KrakenTypes.ENTRYPOINT_KW
            map["entrypoints"] = KrakenTypes.ENTRYPOINTS_KW
            map["when"] = KrakenTypes.WHEN_KW
            map["assert"] = KrakenTypes.ASSERT_KW
            map["set"] = KrakenTypes.SET_KW
            map["default"] = KrakenTypes.DEFAULT_KW
            map["reset"] = KrakenTypes.RESET_KW
            map["to"] = KrakenTypes.TO_KW
            map["mandatory"] = KrakenTypes.MANDATORY_KW
            map["empty"] = KrakenTypes.EMPTY_KW
            map["disabled"] = KrakenTypes.DISABLED_KW
            map["hidden"] = KrakenTypes.HIDDEN_KW
            map["matches"] = KrakenTypes.MATCHES_KW
            map["size"] = KrakenTypes.SIZE_KW
            map["min"] = KrakenTypes.MIN_KW
            map["max"] = KrakenTypes.MAX_KW
            map["length"] = KrakenTypes.LENGTH_KW
            map["number"] = KrakenTypes.NUMBER_KW
            map["step"] = KrakenTypes.STEP_KW
            map["in"] = KrakenTypes.IN_KW
            map["overridable"] = KrakenTypes.OVERRIDABLE_KW
            map["error"] = KrakenTypes.ERROR_KW
            map["warn"] = KrakenTypes.WARN_KW
            map["info"] = KrakenTypes.INFO_KW
            map["dimension"] = KrakenTypes.DIMENSION_KW
            map["function"] = KrakenTypes.FUNCTION_KW
            map["priority"] = KrakenTypes.PRIORITY_KW
            map["description"] = KrakenTypes.DESCRIPTION_KW
            map["notstrict"] = KrakenTypes.NOT_STRICT_KW
            map["forbidtarget"] = KrakenTypes.FORBID_TARGET_KW
            map["forbidreference"] = KrakenTypes.FORBID_REFERENCE_KW
            map["serversideonly"] = KrakenTypes.SERVER_SIDE_ONLY_KW
            map["true"] = KrakenTypes.TRUE_KW
            map["false"] = KrakenTypes.FALSE_KW
            map["null"] = KrakenTypes.NULL_KW
            map["and"] = KrakenTypes.AND_KW
            map["or"] = KrakenTypes.OR_KW
            map["not"] = KrakenTypes.NOT_KW
            map["if"] = KrakenTypes.IF_KW
            map["then"] = KrakenTypes.THEN_KW
            map["else"] = KrakenTypes.ELSE_KW
            map["for"] = KrakenTypes.FOR_KW
            map["every"] = KrakenTypes.EVERY_KW
            map["some"] = KrakenTypes.SOME_KW
            map["return"] = KrakenTypes.RETURN_KW
            map["this"] = KrakenTypes.THIS_KW
            map["instanceof"] = KrakenTypes.INSTANCEOF_KW
            map["typeof"] = KrakenTypes.TYPEOF_KW
            map["satisfies"] = KrakenTypes.SATISFIES_KW
            map
        }
    }
}
