package com.kraken.plugin

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KrakenCompletionTest : BasePlatformTestCase() {

    fun testRuleBodyCompletion() {
        myFixture.configureByText(
            "test.rules",
            """
            Rule "r" On Policy.state {
                <caret>
            }
            """.trimIndent(),
        )
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue("Expected 'Assert' in $strings", strings.contains("Assert"))
        assertTrue("Expected 'Set Mandatory' in $strings", strings.contains("Set Mandatory"))
        assertTrue("Expected 'Reset To' in $strings", strings.contains("Reset To"))
    }

    fun testTopLevelCompletion() {
        myFixture.configureByText("test.rules", "<caret>")
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue("Expected 'Rule' in $strings", strings.contains("Rule"))
        assertTrue("Expected 'EntryPoint' in $strings", strings.contains("EntryPoint"))
    }

    fun testEntryPointSuggestsRuleNames() {
        myFixture.configureByText(
            "test.rules",
            """
            Rule "First rule" On Policy.state {
                Assert true
            }

            Rule "Second rule" On Policy.state {
                Assert false
            }

            EntryPoint "Validation" {
                <caret>
            }
            """.trimIndent(),
        )
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue("Expected rule names in $strings", strings.contains("\"First rule\""))
    }

    fun testDimensionAnnotationSuggestsDeclaredDimensions() {
        myFixture.configureByText(
            "test.rules",
            """
            Dimension "state" : String
            Dimension "plan" : String

            @Dimension(<caret>)
            Rule "r" On Policy.state {
                Assert true
            }
            """.trimIndent(),
        )
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue("Expected dimension names in $strings", strings.contains("\"state\""))
    }
}
