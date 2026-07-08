package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenUnusedRuleInspection
import com.kraken.plugin.navigation.KrakenGotoDeclarationHandler

/**
 * Sémantique stricte des namespaces : une référence située dans un namespace
 * qui ne voit pas la déclaration ne compte pas (usages, navigation, inspection).
 */
class KrakenNamespaceStrictnessTest : BasePlatformTestCase() {

    fun testRuleReferencedOnlyFromBlindNamespaceIsUnused() {
        myFixture.addFileToProject(
            "policy.rules",
            """
            Namespace Policy

            EntryPoint "Broken" {
                "Hidden elsewhere"
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenUnusedRuleInspection())
        myFixture.configureByText(
            "other.rules",
            """
            Namespace Other

            Rule "Hidden elsewhere" On Widget.name {
                Set Hidden
            }
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Rule referenced only from a blind namespace must be unused, got: " +
                    highlights.map { it.description },
            highlights.any { it.description?.startsWith("Rule 'Hidden elsewhere' is not referenced") == true }
        )
    }

    fun testNoDeclarationToUsageNavigationAcrossBlindNamespace() {
        myFixture.addFileToProject(
            "policy.rules",
            """
            Namespace Policy

            EntryPoint "Broken" {
                "Hidden elsewhere"
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "other.rules",
            """
            Namespace Other

            Rule "Hidden<caret> elsewhere" On Widget.name {
                Set Hidden
            }
            """.trimIndent()
        )
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = KrakenGotoDeclarationHandler()
            .getGotoDeclarationTargets(leaf, myFixture.caretOffset, myFixture.editor)
        assertNull("No navigation targets expected across blind namespaces", targets)
    }

    fun testVisibleReferenceStillCounts() {
        myFixture.addFileToProject(
            "policy.rules",
            """
            Namespace Policy

            Include Base

            EntryPoint "Validation" {
                "Base rule"
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenUnusedRuleInspection())
        myFixture.configureByText(
            "base.rules",
            """
            Namespace Base

            Rule "Base rule" On Entity.id {
                Set Mandatory
            }
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "Rule referenced from an including namespace must NOT be unused",
            highlights.any { it.description?.startsWith("Rule 'Base rule' is not referenced") == true }
        )
    }
}
