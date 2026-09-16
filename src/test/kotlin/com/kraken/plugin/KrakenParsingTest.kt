package com.kraken.plugin

import com.intellij.testFramework.ParsingTestCase
import com.kraken.plugin.lang.KrakenParserDefinition

/** Checks that representative `.rules` files parse without PSI error elements. */
class KrakenParsingTest : ParsingTestCase("", "rules", KrakenParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData/parser"

    override fun skipSpaces(): Boolean = true

    fun testFull() = doTest(false, true)

    fun testContexts() = doTest(false, true)

    fun testHeader() = doTest(false, true)

    fun testKel() = doTest(false, true)

    fun testPinRegression() = doTest(false, true)

    fun testCast() = doTest(false, true)
}
