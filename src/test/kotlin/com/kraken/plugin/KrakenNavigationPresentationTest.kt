package com.kraken.plugin

import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Libellés des usages dans le popup de la gouttière.
 *
 * Sans présentation, la plateforme affiche le texte brut : N entrées
 * identiques, sans indication du fichier. Ces tests vérifient que chaque
 * cible porte un texte distinctif *et* sa localisation.
 *
 * Depuis v0.8.1, ce popup n'est plus celui de Ctrl+B — la plateforme y montre
 * sa propre fenêtre d'usages — mais celui de l'icône de gouttière, qui passe
 * par `NavigationGutterIconBuilder.setTargets` et rend donc toujours ces
 * [com.intellij.navigation.ItemPresentation].
 */
class KrakenNavigationPresentationTest : BasePlatformTestCase() {

    private fun presentationOf(element: PsiElement) =
        (element as NavigationItem).presentation

    /** Usages de la déclaration sous le curseur, tels que la gouttière les cible. */
    private fun usagesAtCaret(): List<PsiElement> {
        val source = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("Expected an element at caret", source)
        PsiTreeUtil.getParentOfType(source, KrakenRuleDecl::class.java)?.let {
            return KrakenPsiUtil.findRuleRefsVisibleTo(it)
        }
        PsiTreeUtil.getParentOfType(source, KrakenEntryPointDecl::class.java)?.let {
            return KrakenPsiUtil.findEpRefsVisibleTo(it)
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
            """.trimIndent()
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
            """.trimIndent()
        )

        val targets = usagesAtCaret()
        assertEquals("Rule is referenced twice", 2, targets.size)

        val labels = targets.map { presentationOf(it) }
            .map { "${it?.presentableText} @ ${it?.locationString}" }
            .sorted()

        // Chaque entrée nomme son EntryPoint et son fichier : deux usages de la
        // même règle ne peuvent plus être confondus dans le popup.
        assertEquals(
            listOf(
                "EntryPoint \"Billing\" @ billing.rules",
                "EntryPoint \"Validation\" @ rules.rules",
            ),
            labels
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
            """.trimIndent()
        )

        val presentation = presentationOf(usagesAtCaret().single())
        assertEquals(
            "Namespace disambiguates same-named files across modules",
            "policy.rules · Policy",
            presentation?.locationString
        )
    }

    fun testEntryPointUsagesCarryTheirOwnLabels() {
        myFixture.addFileToProject(
            "composed.rules",
            """
            EntryPoint "Composed" {
                EntryPoint "Reused"
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "base.rules",
            """
            EntryPoint "Reu<caret>sed" {
                "Some rule"
            }
            """.trimIndent()
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
            """.trimIndent()
        )

        val rule = com.intellij.psi.util.PsiTreeUtil
            .findChildOfType(file, KrakenRuleDecl::class.java)!!
        val entryPoint = com.intellij.psi.util.PsiTreeUtil
            .findChildOfType(file, KrakenEntryPointDecl::class.java)!!

        assertEquals("\"A rule\"", presentationOf(rule)?.presentableText)
        assertEquals("decls.rules · Base", presentationOf(rule)?.locationString)
        assertEquals("\"An entry point\"", presentationOf(entryPoint)?.presentableText)
    }
}
