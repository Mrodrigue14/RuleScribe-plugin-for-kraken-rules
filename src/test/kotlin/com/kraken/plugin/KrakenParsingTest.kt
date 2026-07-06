package com.kraken.plugin

import com.intellij.testFramework.ParsingTestCase
import com.kraken.plugin.lang.KrakenParserDefinition

/**
 * Vérifie que le parser accepte des fichiers .rules représentatifs
 * sans produire d'élément d'erreur PSI.
 */
class KrakenParsingTest : ParsingTestCase("", "rules", KrakenParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData/parser"

    override fun skipSpaces(): Boolean = true

    fun testFull() = doTest(false, true)

    fun testContexts() = doTest(false, true)

    fun testHeader() = doTest(false, true)

    fun testKel() = doTest(false, true)
}
