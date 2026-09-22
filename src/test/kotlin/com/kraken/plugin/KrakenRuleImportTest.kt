package com.kraken.plugin

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenImportAmbiguousInspection
import com.kraken.plugin.inspection.KrakenImportNameClashInspection
import com.kraken.plugin.inspection.KrakenImportUnknownNamespaceInspection
import com.kraken.plugin.inspection.KrakenImportUnknownRuleInspection
import com.kraken.plugin.inspection.KrakenUnresolvedRuleRefInspection
import com.kraken.plugin.inspection.KrakenUnusedRuleInspection

/**
 * `Import Rule "X" From Ns` semantics, mirroring the engine: the imported rule is local
 * to the importing namespace regardless of Include, and the engine's four checks are
 * inspected.
 */
class KrakenRuleImportTest : BasePlatformTestCase() {

    private fun addBaseNamespace() {
        myFixture.addFileToProject(
            "base.rules",
            """
            Namespace Base

            Rule "Base rule" On Entity.id {
                Set Mandatory
            }
            """.trimIndent(),
        )
    }

    fun testImportedRuleResolvesWithoutInclude() {
        addBaseNamespace()
        myFixture.enableInspections(KrakenUnresolvedRuleRefInspection())
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Base rule" From Base

            EntryPoint "Validation" {
                "Base rule"
            }
            """.trimIndent(),
        )
        myFixture.assertNotReported("[kve005]")
    }

    fun testNonImportedRuleStaysInvisibleWithoutInclude() {
        addBaseNamespace()
        myFixture.enableInspections(KrakenUnresolvedRuleRefInspection())
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            EntryPoint "Validation" {
                "Base rule"
            }
            """.trimIndent(),
        )
        myFixture.assertReported("[kve005]")
    }

    fun testImportedRuleIsNotFlaggedUnused() {
        myFixture.addFileToProject(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Base rule" From Base

            EntryPoint "Validation" {
                "Base rule"
            }
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenUnusedRuleInspection())
        myFixture.configureByText(
            "base.rules",
            """
            Namespace Base

            Rule "Base rule" On Entity.id {
                Set Mandatory
            }
            """.trimIndent(),
        )
        myFixture.assertNotReported("Rule 'Base rule' is not referenced")
    }

    fun testCompletionSuggestsImportedRule() {
        addBaseNamespace()
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Base rule" From Base

            EntryPoint "Validation" {
                <caret>
            }
            """.trimIndent(),
        )
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue(
            "Imported rule expected in completion, got: $strings",
            strings.any { it.contains("Base rule") },
        )
    }

    fun testUnknownNamespaceInspection() {
        myFixture.enableInspections(KrakenImportUnknownNamespaceInspection())
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Ghost" From Nowhere
            """.trimIndent(),
        )
        myFixture.assertReported("[kbs026] Cannot import rule 'Ghost' from namespace 'Nowhere' to 'Policy', because namespace does not exist.")
    }

    fun testUnknownRuleInspection() {
        addBaseNamespace()
        myFixture.enableInspections(KrakenImportUnknownRuleInspection())
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Ghost" From Base
            """.trimIndent(),
        )
        myFixture.assertReported("[kbs025] Cannot import rule 'Ghost' from namespace 'Base' to 'Policy', because rule does not exist.")
    }

    fun testNameClashInspection() {
        addBaseNamespace()
        myFixture.enableInspections(KrakenImportNameClashInspection())
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Base rule" From Base

            Rule "Base rule" On Entity.id {
                Set Mandatory
            }
            """.trimIndent(),
        )
        myFixture.assertReported(
            "[kbs027] Cannot import rule 'Base rule' from namespace 'Base' to 'Policy', because rule is already defined.",
        )
    }

    fun testAmbiguousImportInspection() {
        addBaseNamespace()
        myFixture.addFileToProject(
            "other.rules",
            """
            Namespace Other

            Rule "Base rule" On Entity.id {
                Set Mandatory
            }
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenImportAmbiguousInspection())
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Base rule" From Base
            Import Rule "Base rule" From Other
            """.trimIndent(),
        )
        myFixture.assertReported("[kbs027] Cannot import rule 'Base rule' to")
    }
}
