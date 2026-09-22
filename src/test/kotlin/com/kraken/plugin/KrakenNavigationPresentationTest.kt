package com.kraken.plugin

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenDeclarations
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Labels of usages in the gutter popup.
 *
 * Without a presentation the platform shows raw text: identical entries with no file.
 * These tests check that each target has distinguishing text and its location. The
 * gutter icon goes through `NavigationGutterIconBuilder.setTargets`, so it renders these
 * [ItemPresentation]s.
 */
class KrakenNavigationPresentationTest : BasePlatformTestCase() {

    private fun presentationOf(element: PsiElement) = (element as NavigationItem).presentation

    /** Usages of the declaration at the caret, as the gutter targets them. */
    private fun usagesAtCaret(): List<PsiElement> {
        val source = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("Expected an element at caret", source)
        PsiTreeUtil.getParentOfType(source, KrakenRuleDecl::class.java)?.let {
            return KrakenDeclarations.findRuleRefsVisibleTo(it)
        }
        PsiTreeUtil.getParentOfType(source, KrakenEntryPointDecl::class.java)?.let {
            return KrakenDeclarations.findEpRefsVisibleTo(it)
        }
        fail("No declaration at caret")
        return emptyList()
    }

    fun testMultipleUsagesAreDistinguishedByEntryPointAndFile() {
        myFixture.addFileToProject(
            "billing.rules",
            """
            EntryPoint "Billing" {
                "Shared rule"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "rules.rules",
            """
            Rule "Shared <caret>rule" On Policy.state {
                Assert true
            }

            EntryPoint "Validation" {
                "Shared rule"
            }
            """.trimIndent(),
        )

        val targets = usagesAtCaret()
        assertEquals("Rule is referenced twice", 2, targets.size)

        val labels = targets.map { presentationOf(it) }
            .map { "${it?.presentableText} @ ${it?.locationString}" }
            .sorted()

        // Each entry names its EntryPoint and file, so two usages of one rule cannot be
        // confused.
        assertEquals(
            listOf(
                "EntryPoint \"Billing\" @ billing.rules",
                "EntryPoint \"Validation\" @ rules.rules",
            ),
            labels,
        )
        assertEquals("Labels must be distinct", 2, labels.toSet().size)
    }

    fun testLocationIncludesNamespaceWhenDeclared() {
        myFixture.configureByText(
            "policy.rules",
            """
            Namespace Policy

            Rule "Namespaced <caret>rule" On Policy.state {
                Assert true
            }

            EntryPoint "Validation" {
                "Namespaced rule"
            }
            """.trimIndent(),
        )

        val presentation = presentationOf(usagesAtCaret().single())
        assertEquals(
            "Namespace disambiguates same-named files across modules",
            "policy.rules · Policy",
            presentation?.locationString,
        )
    }

    fun testEntryPointUsagesCarryTheirOwnLabels() {
        myFixture.addFileToProject(
            "composed.rules",
            """
            EntryPoint "Composed" {
                EntryPoint "Reused"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "base.rules",
            """
            EntryPoint "Reu<caret>sed" {
                "Some rule"
            }
            """.trimIndent(),
        )

        val presentation = presentationOf(usagesAtCaret().single())
        assertEquals("EntryPoint \"Composed\"", presentation?.presentableText)
        assertEquals("composed.rules", presentation?.locationString)
    }

    fun testDeclarationsPresentTheirNameAndFile() {
        val file = myFixture.configureByText(
            "decls.rules",
            """
            Namespace Base

            Rule "A rule" On Policy.state {
                Assert true
            }

            EntryPoint "An entry point" {
                "A rule"
            }
            """.trimIndent(),
        )

        val rule = PsiTreeUtil
            .findChildOfType(file, KrakenRuleDecl::class.java)!!
        val entryPoint = PsiTreeUtil
            .findChildOfType(file, KrakenEntryPointDecl::class.java)!!

        assertEquals("\"A rule\"", presentationOf(rule)?.presentableText)
        assertEquals("decls.rules · Base", presentationOf(rule)?.locationString)
        assertEquals("\"An entry point\"", presentationOf(entryPoint)?.presentableText)
    }
}
