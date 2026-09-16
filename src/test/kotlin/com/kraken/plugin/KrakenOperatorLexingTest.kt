package com.kraken.plugin

import com.intellij.psi.TokenType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.parser.KrakenLexer
import com.kraken.plugin.parser.KrakenTypes

/**
 * Operator tokenisation.
 *
 * Only the operators of `Common.g4` are recognised, so a typo fails at the right offset
 * and `>=` / `<=` are single tokens rather than `GT` followed by `OP('=')`.
 */
class KrakenOperatorLexingTest : BasePlatformTestCase() {

    private fun tokens(source: String): List<Pair<String, String>> {
        val lexer = KrakenLexer()
        lexer.start(source)
        val out = mutableListOf<Pair<String, String>>()
        while (lexer.tokenType != null) {
            val type = lexer.tokenType!!
            if (type != TokenType.WHITE_SPACE) {
                val name = when (type) {
                    KrakenTypes.OP -> "OP"

                    // `|` has its own token because it also separates union type members.
                    KrakenTypes.PIPE -> "PIPE"

                    TokenType.BAD_CHARACTER -> "BAD"

                    else -> type.toString()
                }
                out += name to lexer.tokenText
            }
            lexer.advance()
        }
        return out
    }

    private fun operators(source: String): List<Pair<String, String>> = tokens(source).filter { it.first in setOf("OP", "PIPE", "BAD") }

    fun testWideComparisonsAreSingleTokens() {
        assertEquals(listOf("OP" to ">="), operators("a >= b"))
        assertEquals(listOf("OP" to "<="), operators("a <= b"))
    }

    fun testTwoCharOperatorsAreSingleTokens() {
        assertEquals(listOf("OP" to "!="), operators("a != b"))
        assertEquals(listOf("OP" to "=="), operators("a == b"))
        assertEquals(listOf("OP" to "&&"), operators("a && b"))
        assertEquals(listOf("OP" to "||"), operators("a || b"))
        assertEquals(listOf("OP" to "**"), operators("a ** b"))
    }

    fun testSingleCharOperatorsStillLex() {
        for (op in listOf("+", "-", "=", "%")) {
            assertEquals("operator $op", listOf("OP" to op), operators("a $op b"))
        }
    }

    /**
     * A lone `|` is a token distinct from `OP`: it separates union type members
     * (`Date | DateTime`), where the grammar must accept it without accepting any operator.
     * `||` stays an `OP`.
     */
    fun testLoneBarIsItsOwnTokenButDoubleBarIsNot() {
        assertEquals(listOf("PIPE" to "|"), operators("a | b"))
        assertEquals(listOf("OP" to "||"), operators("a || b"))
    }

    fun testGarbageOperatorRunIsRejectedPerCharacter() {
        assertEquals(
            listOf("BAD" to "&", "PIPE" to "|", "BAD" to "&", "BAD" to "~"),
            operators("a &|&~ b"),
        )
    }

    /** `^` and `~` are not part of any Common.g4 operator. */
    fun testCharactersOutsideTheGrammarAreRejected() {
        assertEquals(listOf("BAD" to "^", "BAD" to "~"), operators("a ^~ b"))
    }

    /** A lone `&` is not an operator either; only `&&` is. */
    fun testLoneAmpersandIsRejected() {
        assertEquals(listOf("BAD" to "&"), operators("a & b"))
    }

    /**
     * `>>` stays two `GT`s. A generic bound can close twice in a row
     * (`Function <T is <G>>`), and the `kvf005` inspection depends on it.
     */
    fun testDoubleAngleClosesTwice() {
        assertEquals(listOf(">" to ">", ">" to ">"), tokens("<T is <G>>").takeLast(2))
        assertEquals(emptyList<Pair<String, String>>(), operators("a >> b"))
    }

    fun testAngleBracketsAndDatesAreUnaffected() {
        assertEquals(emptyList<Pair<String, String>>(), operators("a < b"))
        assertEquals(emptyList<Pair<String, String>>(), operators("2020-01-01"))
        assertEquals(listOf("OP" to "-"), operators("-1"))
    }
}
