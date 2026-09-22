package com.kraken.plugin

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenUnusedRuleInspection
import com.kraken.plugin.psi.KrakenNamespaces
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.psi.KrakenRuleRef

/**
 * Strict namespace semantics: a reference in a namespace that cannot see the
 * declaration does not count (usages, navigation, inspection).
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
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenUnusedRuleInspection())
        myFixture.configureByText(
            "other.rules",
            """
            Namespace Other

            Rule "Hidden elsewhere" On Widget.name {
                Set Hidden
            }
            """.trimIndent(),
        )
        myFixture.assertReported("Rule 'Hidden elsewhere' is not referenced")
    }

    fun testNoDeclarationToUsageNavigationAcrossBlindNamespace() {
        myFixture.addFileToProject(
            "policy.rules",
            """
            Namespace Policy

            EntryPoint "Broken" {
                "Hidden elsewhere"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "other.rules",
            """
            Namespace Other

            Rule "Hidden<caret> elsewhere" On Widget.name {
                Set Hidden
            }
            """.trimIndent(),
        )
        val declaration = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), KrakenRuleDecl::class.java)!!
        assertEmpty("No usages expected across blind namespaces", ReferencesSearch.search(declaration).findAll())
    }

    /** The cached namespace model follows edits of an already opened file. */
    fun testAddingAnIncludeMakesTheOtherNamespaceVisible() {
        val declarations = myFixture.addFileToProject(
            "base.rules",
            """
            Namespace Base

            Rule "Shared" On Widget.name {
                Set Hidden
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy
            <caret>
            EntryPoint "Uses shared" {
                "Shared"
            }
            """.trimIndent(),
        )
        assertFalse(KrakenNamespaces.sees(myFixture.file, declarations))

        myFixture.type("Include Base")
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue(KrakenNamespaces.sees(myFixture.file, declarations))
    }

    /** The cached namespace model sees a file added after it was built. */
    fun testFileAddedLaterBecomesVisible() {
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy
            Include Base

            EntryPoint "Uses shared" {
                "Shared"
            }
            """.trimIndent(),
        )
        val item = allOf<KrakenRuleRef>(myFixture.file).single()
        assertNull(item.reference.resolve())

        myFixture.addFileToProject(
            "base.rules",
            """
            Namespace Base

            Rule "Shared" On Widget.name {
                Set Hidden
            }
            """.trimIndent(),
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertNotNull(item.reference.resolve())
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
}
