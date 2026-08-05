package com.kraken.plugin

import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.navigation.KrakenGotoDeclarationHandler
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.psi.KrakenRuleRef

/**
 * Correction des cibles de Ctrl+B, dans les deux sens.
 *
 * [KrakenNavigationPresentationTest] couvre les *libellés* du popup ; ici on
 * vérifie que les cibles elles-mêmes sont les bonnes — et surtout qu'aucune ne
 * manque : proposer une seule implémentation là où plusieurs sont légitimes
 * revient à en choisir une au hasard de l'ordre de l'index.
 */
class KrakenBidirectionalNavigationTest : BasePlatformTestCase() {

    /** Cibles que Ctrl+B produirait sur le nom porté par [element]. */
    private fun targetsFor(element: PsiElement): List<PsiElement> {
        val offset = element.textOffset
        val leaf = element.containingFile.findElementAt(offset)
        assertNotNull("Expected a leaf at offset $offset", leaf)
        return KrakenGotoDeclarationHandler()
            .getGotoDeclarationTargets(leaf, offset, myFixture.editor)
            .orEmpty()
            .toList()
    }

    private fun labelsOf(targets: List<PsiElement>): List<String> =
        targets.map { (it as NavigationItem).presentation }
            .map { "${it?.presentableText} @ ${it?.locationString}" }
            .sorted()

    private inline fun <reified T : PsiElement> allOf(file: PsiElement): List<T> =
        PsiTreeUtil.findChildrenOfType(file, T::class.java).toList()

    // ------------------------------------------------------------------
    // Sens 1 : item d'EntryPoint -> implémentation
    // ------------------------------------------------------------------

    /** Chaque item d'un EntryPoint mène à *sa* règle, pas à la première venue. */
    fun testEachEntryPointItemResolvesToItsOwnRule() {
        val file = myFixture.configureByText(
            "coverage.rules",
            """
            Rule "Check limit" On Policy.limit {
                Assert true
            }

            Rule "Check state" On Policy.state {
                Assert true
            }

            Rule "Check term" On Policy.term {
                Assert true
            }

            EntryPoint "Validation" {
                "Check limit", "Check state", "Check term"
            }
            """.trimIndent()
        )

        val declarations = allOf<KrakenRuleDecl>(file).associateBy { it.name }
        assertEquals(3, declarations.size)

        allOf<KrakenRuleRef>(file).forEach { reference ->
            val target = targetsFor(reference).singleOrNull()
            assertNotNull("No target for \"${reference.ruleName}\"", target)
            assertSame(
                "\"${reference.ruleName}\" must land on its own declaration",
                declarations[reference.ruleName],
                target
            )
        }
    }

    /**
     * Deux namespaces indépendants déclarent la même règle. La visibilité
     * Kraken interdit de traverser : chaque EntryPoint doit rester chez lui.
     */
    fun testEntryPointItemResolvesWithinItsOwnNamespace() {
        val other = myFixture.addFileToProject(
            "other.rules",
            """
            Namespace Other

            Rule "Shared rule" On Policy.state {
                Assert false
            }

            EntryPoint "Other validation" {
                "Shared rule"
            }
            """.trimIndent()
        )
        val base = myFixture.configureByText(
            "base.rules",
            """
            Namespace Base

            Rule "Shared rule" On Policy.state {
                Assert true
            }

            EntryPoint "Base validation" {
                "Shared rule"
            }
            """.trimIndent()
        )

        listOf(base, other).forEach { file ->
            val target = targetsFor(allOf<KrakenRuleRef>(file).single()).single()
            assertEquals(
                "Reference must resolve inside its own namespace",
                file.name,
                target.containingFile.name
            )
        }
    }

    /**
     * Variantes `@Dimension` : même nom, même fichier, deux implémentations
     * également valides. Ctrl+B doit proposer les deux — c'est précisément le
     * cas que KrakenDuplicateRuleInspection laisse passer volontairement.
     */
    fun testDimensionVariantsAreAllOffered() {
        val file = myFixture.configureByText(
            "dimensioned.rules",
            """
            @Dimension("plan", "GOLD")
            Rule "Coverage limit" On Policy.limit {
                Assert true
            }

            @Dimension("plan", "SILVER")
            Rule "Coverage limit" On Policy.limit {
                Assert false
            }

            EntryPoint "Validation" {
                "Coverage limit"
            }
            """.trimIndent()
        )

        val targets = targetsFor(allOf<KrakenRuleRef>(file).single())
        assertEquals("Both dimensional variants are valid targets", 2, targets.size)

        // Même nom, même fichier : sans l'annotation le popup afficherait deux
        // lignes rigoureusement identiques.
        assertEquals(
            listOf(
                "\"Coverage limit\" @Dimension(\"plan\", \"GOLD\") @ dimensioned.rules",
                "\"Coverage limit\" @Dimension(\"plan\", \"SILVER\") @ dimensioned.rules",
            ),
            labelsOf(targets)
        )
    }

    /** `Import Rule` court-circuite Include : la cible reste le namespace source. */
    fun testImportedRuleResolvesToItsSourceNamespace() {
        myFixture.addFileToProject(
            "library.rules",
            """
            Namespace Library

            Rule "Imported rule" On Policy.state {
                Assert true
            }
            """.trimIndent()
        )
        val consumer = myFixture.configureByText(
            "consumer.rules",
            """
            Namespace Consumer
            Import Rule "Imported rule" From Library

            EntryPoint "Validation" {
                "Imported rule"
            }
            """.trimIndent()
        )

        val target = targetsFor(allOf<KrakenRuleRef>(consumer).single()).single()
        assertEquals("library.rules", target.containingFile.name)
    }

    /** Item `EntryPoint "x"` imbriqué : même exigence que pour les règles. */
    fun testNestedEntryPointItemResolvesToItsDeclaration() {
        val file = myFixture.configureByText(
            "composed.rules",
            """
            EntryPoint "Reused" {
                "Some rule"
            }

            EntryPoint "Composed" {
                EntryPoint "Reused"
            }
            """.trimIndent()
        )

        val target = targetsFor(allOf<KrakenEpRef>(file).single()).single()
        assertSame(allOf<KrakenEntryPointDecl>(file).first { it.name == "Reused" }, target)
    }

    // ------------------------------------------------------------------
    // Sens 2 : implémentation -> EntryPoint(s)
    // ------------------------------------------------------------------

    /** Une règle partagée par plusieurs EntryPoints : Ctrl+B les propose tous. */
    fun testImplementationOffersEveryEntryPointReferencingIt() {
        myFixture.addFileToProject(
            "billing.rules",
            """
            EntryPoint "Billing" {
                "Shared rule"
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "quoting.rules",
            """
            EntryPoint "Quoting" {
                "Shared rule"
            }
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "rules.rules",
            """
            Rule "Shared rule" On Policy.state {
                Assert true
            }

            EntryPoint "Validation" {
                "Shared rule"
            }
            """.trimIndent()
        )

        val targets = targetsFor(allOf<KrakenRuleDecl>(file).single())
        assertEquals(
            listOf(
                "EntryPoint \"Billing\" @ billing.rules",
                "EntryPoint \"Quoting\" @ quoting.rules",
                "EntryPoint \"Validation\" @ rules.rules",
            ),
            labelsOf(targets)
        )
    }

    /**
     * L'inverse du cas précédent : un EntryPoint d'un namespace qui ne voit pas
     * la déclaration n'est pas une cible, même s'il cite le même nom. Sans ce
     * filtre, Ctrl+B renverrait vers un EntryPoint qui, lui, résout ailleurs.
     */
    fun testImplementationIgnoresEntryPointsThatCannotSeeIt() {
        myFixture.addFileToProject(
            "other.rules",
            """
            Namespace Other

            Rule "Shared rule" On Policy.state {
                Assert false
            }

            EntryPoint "Other validation" {
                "Shared rule"
            }
            """.trimIndent()
        )
        val base = myFixture.configureByText(
            "base.rules",
            """
            Namespace Base

            Rule "Shared rule" On Policy.state {
                Assert true
            }

            EntryPoint "Base validation" {
                "Shared rule"
            }
            """.trimIndent()
        )

        val targets = targetsFor(allOf<KrakenRuleDecl>(base).single())
        assertEquals(
            listOf("EntryPoint \"Base validation\" @ base.rules · Base"),
            labelsOf(targets)
        )
    }

    /** Un EntryPoint réutilisé par plusieurs autres propose lui aussi tous ses appelants. */
    fun testEntryPointDeclarationOffersEveryCaller() {
        myFixture.addFileToProject(
            "first.rules",
            """
            EntryPoint "First" {
                EntryPoint "Reused"
            }
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "base.rules",
            """
            EntryPoint "Reused" {
                "Some rule"
            }

            EntryPoint "Second" {
                EntryPoint "Reused"
            }
            """.trimIndent()
        )

        val declaration = allOf<KrakenEntryPointDecl>(file).first { it.name == "Reused" }
        assertEquals(
            listOf(
                "EntryPoint \"First\" @ first.rules",
                "EntryPoint \"Second\" @ base.rules",
            ),
            labelsOf(targetsFor(declaration))
        )
    }
}
