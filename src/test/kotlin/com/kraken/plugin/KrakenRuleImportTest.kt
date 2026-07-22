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
 * Sémantique des `Import Rule "X" From Ns` (miroir du moteur) : la règle
 * importée est traitée comme locale au namespace importateur, indépendamment
 * d'Include, et les quatre validations du moteur sont inspectées.
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
            """.trimIndent()
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
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "Imported rule must resolve without Include, got: " +
                highlights.map { it.description },
            highlights.any { it.description?.startsWith("Unknown rule 'Base rule'") == true }
        )
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
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Without import nor Include the rule must stay unresolved",
            highlights.any { it.description?.startsWith("Unknown rule 'Base rule'") == true }
        )
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
            "A rule referenced through an import must not be flagged unused, got: " +
                highlights.map { it.description },
            highlights.any { it.description?.startsWith("Rule 'Base rule' is not referenced") == true }
        )
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
            """.trimIndent()
        )
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue(
            "Imported rule expected in completion, got: $strings",
            strings.any { it.contains("Base rule") }
        )
    }

    fun testUnknownNamespaceInspection() {
        myFixture.enableInspections(KrakenImportUnknownNamespaceInspection())
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Ghost" From Nowhere
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Unknown source namespace must be flagged, got: " +
                highlights.map { it.description },
            highlights.any { it.description == "Unknown namespace 'Nowhere'" }
        )
    }

    fun testUnknownRuleInspection() {
        addBaseNamespace()
        myFixture.enableInspections(KrakenImportUnknownRuleInspection())
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Ghost" From Base
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Missing rule in the source namespace must be flagged, got: " +
                highlights.map { it.description },
            highlights.any {
                it.description == "Rule 'Ghost' does not exist in namespace 'Base'"
            }
        )
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
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Import colliding with a local rule must be flagged, got: " +
                highlights.map { it.description },
            highlights.any {
                it.description ==
                    "Imported rule 'Base rule' collides with a rule declared in this namespace"
            }
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
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenImportAmbiguousInspection())
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Import Rule "Base rule" From Base
            Import Rule "Base rule" From Other
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "A rule imported twice must be flagged as ambiguous, got: " +
                highlights.map { it.description },
            highlights.any { it.description?.startsWith("Rule 'Base rule' is imported more than once") == true }
        )
    }
}
