package com.kraken.plugin

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Navigation et résolution entre fichiers : le cas d'usage réel où les
 * EntryPoints vivent dans un fichier séparé des déclarations de règles.
 */
class KrakenCrossFileNavigationTest : BasePlatformTestCase() {

    fun testCtrlClickResolvesAcrossFiles() {
        myFixture.addFileToProject(
            "rules.rules",
            """
            Rule "Cross file rule" On Policy.state {
                Assert true
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "entrypoints.rules",
            """
            EntryPoint "Validation" {
                "Cross file<caret> rule"
            }
            """.trimIndent(),
        )
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Expected a reference at caret", reference)
        val target = reference!!.resolve()
        assertTrue("Expected a KrakenRuleDecl, got $target", target is KrakenRuleDecl)
        assertEquals("Cross file rule", (target as KrakenRuleDecl).name)
        assertEquals(
            "Target should live in the other file",
            "rules.rules",
            target.containingFile.name,
        )
    }

    fun testIncludeMakesOtherNamespaceVisible() {
        myFixture.addFileToProject(
            "base.rules",
            """
            Namespace Base

            Rule "Base rule" On BaseEntity.id {
                Set Mandatory
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Include Base

            EntryPoint "Validation" {
                "Base<caret> rule"
            }
            """.trimIndent(),
        )
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        val target = reference!!.resolve()
        assertTrue("Included namespace rule should resolve, got $target", target is KrakenRuleDecl)
    }

    fun testTransitiveIncludeResolution() {
        myFixture.addFileToProject(
            "c.rules",
            """
            Namespace C

            Rule "Deep rule" On X.y {
                Assert true
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "b.rules",
            """
            Namespace B

            Include C
            """.trimIndent(),
        )
        myFixture.configureByText(
            "a.rules",
            """
            Namespace A

            Include B

            EntryPoint "E" {
                "Deep<caret> rule"
            }
            """.trimIndent(),
        )
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertTrue(
            "Transitively included rule should resolve",
            reference!!.resolve() is KrakenRuleDecl,
        )
    }

    fun testEntryPointCompletionSeesOtherFiles() {
        myFixture.addFileToProject(
            "rules.rules",
            """
            Rule "Alpha rule" On Policy.a {
                Assert true
            }

            Rule "Beta rule" On Policy.b {
                Assert true
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "entrypoints.rules",
            """
            EntryPoint "Validation" {
                <caret>
            }
            """.trimIndent(),
        )
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue("Expected rules from other file in $strings", strings.contains("\"Alpha rule\""))
        assertTrue("Expected rules from other file in $strings", strings.contains("\"Beta rule\""))
    }

    fun testUnresolvedWhenNamespaceNotIncluded() {
        myFixture.addFileToProject(
            "other.rules",
            """
            Namespace Other

            Rule "Invisible rule" On X.y {
                Assert true
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            EntryPoint "E" {
                "Invisible<caret> rule"
            }
            """.trimIndent(),
        )
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertNull(
            "Rule from a non-included namespace must not resolve",
            reference!!.resolve(),
        )
    }
}
