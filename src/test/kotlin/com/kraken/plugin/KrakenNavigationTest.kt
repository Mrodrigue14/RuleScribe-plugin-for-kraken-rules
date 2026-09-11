package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenRuleDecl

class KrakenNavigationTest : BasePlatformTestCase() {

    fun testRuleReferenceResolvesToDeclaration() {
        myFixture.configureByText(
            "test.rules",
            """
            Rule "Target rule" On Policy.state {
                Assert true
            }

            EntryPoint "Validation" {
                "Target<caret> rule"
            }
            """.trimIndent(),
        )
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Expected a reference at caret", reference)
        val target = reference!!.resolve()
        assertTrue("Expected resolution to a KrakenRuleDecl, got $target", target is KrakenRuleDecl)
        assertEquals("Target rule", (target as KrakenRuleDecl).name)
    }

    fun testRenameRuleUpdatesReferences() {
        myFixture.configureByText(
            "test.rules",
            """
            Rule "Old<caret> name" On Policy.state {
                Assert true
            }

            EntryPoint "Validation" {
                "Old name"
            }
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("New name")
        val text = myFixture.file.text
        assertTrue("Declaration should be renamed:\n$text", text.contains("Rule \"New name\""))
        assertTrue("Reference should be renamed:\n$text", text.contains("\"New name\"\n"))
        assertFalse("No stale old name expected:\n$text", text.contains("Old name"))
    }
}
