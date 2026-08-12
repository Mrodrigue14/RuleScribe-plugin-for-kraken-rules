package com.kraken.plugin

import com.intellij.psi.TokenType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.parser.KrakenLexer
import com.kraken.plugin.parser.KrakenTypes

/**
 * Découpage des opérateurs.
 *
 * Le lexer avalait auparavant toute suite de `+-=!?|&%^~` en un seul token
 * `OP`, que la grammaire acceptait ensuite sans broncher : `a &|&~ b` était du
 * Kraken valide. Il ne reconnaît plus que les opérateurs de `Common.g4`, ce qui
 * a deux effets — une faute de frappe devient une erreur au bon offset, et
 * `>=` / `<=` deviennent des tokens entiers au lieu de `GT` suivi de `OP('=')`.
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
                    TokenType.BAD_CHARACTER -> "BAD"
                    else -> type.toString()
                }
                out += name to lexer.tokenText
            }
            lexer.advance()
        }
        return out
    }

    private fun operators(source: String): List<Pair<String, String>> =
        tokens(source).filter { it.first == "OP" || it.first == "BAD" }

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
        for (op in listOf("+", "-", "=", "%", "|")) {
            assertEquals("opérateur $op", listOf("OP" to op), operators("a $op b"))
        }
    }

    /** Le cas qui motivait le changement. */
    fun testGarbageOperatorRunIsRejectedPerCharacter() {
        assertEquals(
            listOf("BAD" to "&", "OP" to "|", "BAD" to "&", "BAD" to "~"),
            operators("a &|&~ b")
        )
    }

    /** `^` et `~` n'existent dans aucun opérateur de Common.g4. */
    fun testCharactersOutsideTheGrammarAreRejected() {
        assertEquals(listOf("BAD" to "^", "BAD" to "~"), operators("a ^~ b"))
    }

    /** `&` seul n'existe pas non plus : seul `&&` est un opérateur. */
    fun testLoneAmpersandIsRejected() {
        assertEquals(listOf("BAD" to "&"), operators("a & b"))
    }

    /** Les bornes génériques et les dates ne doivent rien perdre au change. */
    fun testAngleBracketsAndDatesAreUnaffected() {
        assertEquals(emptyList<Pair<String, String>>(), operators("a < b"))
        assertEquals(emptyList<Pair<String, String>>(), operators("2020-01-01"))
        assertEquals(listOf("OP" to "-"), operators("-1"))
    }
}
