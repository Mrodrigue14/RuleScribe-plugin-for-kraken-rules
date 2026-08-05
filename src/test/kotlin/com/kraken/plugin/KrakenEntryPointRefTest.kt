package com.kraken.plugin

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenUnresolvedRuleRefInspection
import com.kraken.plugin.psi.KrakenEntryPointDecl

class KrakenEntryPointRefTest : BasePlatformTestCase() {

    fun testNestedEntryPointReferenceResolves() {
        myFixture.addFileToProject(
            "other.rules",
            """
            EntryPoint "Nested target" {
                "Some rule"
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.rules",
            """
            EntryPoint "Main" {
                EntryPoint "Nested<caret> target"
            }
            """.trimIndent()
        )
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Expected a reference on nested EntryPoint item", reference)
        val target = reference!!.resolve()
        assertTrue("Expected an entry point declaration, got $target", target is KrakenEntryPointDecl)
        assertEquals("other.rules", (target as KrakenEntryPointDecl).containingFile.name)
    }

    fun testUnknownNestedEntryPointIsReported() {
        myFixture.enableInspections(KrakenUnresolvedRuleRefInspection())
        myFixture.configureByText(
            "main.rules",
            """
            EntryPoint "Main" {
                EntryPoint "Missing"
            }
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Expected 'Unknown entry point' problem, got: ${highlights.map { it.description }}",
            highlights.any { it.description == "Unknown entry point 'Missing'" }
        )
    }

    fun testCompletionSuggestsOtherEntryPointsAndExcludesListed() {
        myFixture.configureByText(
            "test.rules",
            """
            Rule "Listed rule" On Policy.a {
                Assert true
            }

            Rule "Available rule" On Policy.b {
                Assert true
            }

            EntryPoint "Other" {
                "Available rule"
            }

            EntryPoint "Main" {
                "Listed rule",
                <caret>
            }
            """.trimIndent()
        )
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue("Expected available rule in $strings", strings.contains("\"Available rule\""))
        assertFalse("Already listed rule must be excluded: $strings", strings.contains("\"Listed rule\""))
        assertTrue("Expected other entry point in $strings", strings.contains("EntryPoint \"Other\""))
        assertFalse("Current entry point must be excluded: $strings", strings.contains("EntryPoint \"Main\""))
    }

    fun testRenameEntryPointUpdatesNestedReference() {
        myFixture.configureByText(
            "test.rules",
            """
            EntryPoint "Old<caret> ep" {
                "Some rule"
            }

            EntryPoint "Main" {
                EntryPoint "Old ep"
            }
            """.trimIndent()
        )
        myFixture.renameElementAtCaret("New ep")
        val text = myFixture.file.text
        assertTrue("Declaration renamed:\n$text", text.contains("EntryPoint \"New ep\" {"))
        assertTrue("Nested reference renamed:\n$text", text.contains("EntryPoint \"New ep\"\n"))
        assertFalse("No stale name expected:\n$text", text.contains("Old ep"))
    }
}

// Navigation déclaration -> usages (Ctrl+clic sur le nom déclaré)
class KrakenDeclarationToUsagesTest : com.intellij.testFramework.fixtures.BasePlatformTestCase() {

    /**
     * Depuis v0.8.1, Ctrl+B sur une déclaration ne passe plus par notre
     * handler : celui-ci s'abstient, et « Go To Declaration or Usages » de la
     * plateforme prend le relais pour afficher sa popup d'usages. Le test
     * verrouille donc les deux moitiés du contrat — le handler se tait, et la
     * recherche de références trouve bien l'usage.
     */
    fun testDeclarationLeavesUsagesToThePlatform() {
        myFixture.addFileToProject(
            "eps.rules",
            """
            EntryPoint "Validation" {
                "Policy code mandatory"
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "rules.rules",
            """
            Rule "Policy code<caret> mandatory" On Policy.policyCd {
                Set Mandatory
            }
            """.trimIndent()
        )

        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = com.kraken.plugin.navigation.KrakenGotoDeclarationHandler()
            .getGotoDeclarationTargets(leaf, myFixture.caretOffset, myFixture.editor)
        assertNull("The handler must not shadow the platform's usages popup", targets)

        val declaration = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            myFixture.file, com.kraken.plugin.psi.KrakenRuleDecl::class.java
        ).first()
        val usages = myFixture.findUsages(declaration)
        assertEquals(1, usages.size)
        assertEquals("eps.rules", usages.first().file?.name)
    }

    fun testFindUsagesWorksForMultiWordRuleName() {
        myFixture.addFileToProject(
            "eps.rules",
            """
            EntryPoint "Validation" {
                "Multi word rule name"
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "rules.rules",
            """
            Rule "Multi word<caret> rule name" On Policy.x {
                Assert true
            }
            """.trimIndent()
        )
        val declaration = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            myFixture.file, com.kraken.plugin.psi.KrakenRuleDecl::class.java
        ).first()
        val usages = myFixture.findUsages(declaration)
        assertEquals("Expected exactly one usage, got $usages", 1, usages.size)
    }
}
