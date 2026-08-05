package com.kraken.plugin

import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.navigation.KrakenGotoDeclarationHandler
import com.kraken.plugin.navigation.KrakenReferencesCodeVisionProvider
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

    /**
     * Depuis v0.8.1 ce sens ne passe plus par [KrakenGotoDeclarationHandler] :
     * la plateforme affiche sa propre popup d'usages, alimentée par
     * `ReferencesSearch`, et l'inlay « N usages » compte la même chose. Les
     * tests suivent le comportement à son nouvel emplacement — la sémantique
     * de visibilité, elle, est inchangée.
     */
    private fun usageCountOf(declaration: PsiElement): Int =
        myFixture.findUsages(declaration as com.intellij.psi.PsiNamedElement).size

    private fun codeVisionHintOf(declaration: PsiElement): String? =
        KrakenReferencesCodeVisionProvider().getHint(declaration, declaration.containingFile)

    /** Une règle partagée par plusieurs EntryPoints : tous sont des usages. */
    fun testImplementationIsUsedByEveryEntryPointReferencingIt() {
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

        val declaration = allOf<KrakenRuleDecl>(file).single()
        assertEquals(3, usageCountOf(declaration))
        assertEquals("3 usages", codeVisionHintOf(declaration))
        assertEquals(
            listOf("billing.rules", "quoting.rules", "rules.rules"),
            myFixture.findUsages(declaration).mapNotNull { it.file?.name }.sorted()
        )
    }

    /**
     * L'inverse : un EntryPoint d'un namespace qui ne voit pas la déclaration
     * n'est pas un usage, même s'il cite le même nom — il résout ailleurs.
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

        val declaration = allOf<KrakenRuleDecl>(base).single()
        assertEquals("1 usage", codeVisionHintOf(declaration))
        assertEquals(
            listOf("base.rules"),
            myFixture.findUsages(declaration).mapNotNull { it.file?.name }
        )
    }

    /** Un EntryPoint réutilisé par plusieurs autres compte tous ses appelants. */
    fun testEntryPointDeclarationCountsEveryCaller() {
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
        assertEquals("2 usages", codeVisionHintOf(declaration))
        assertEquals(
            listOf("base.rules", "first.rules"),
            myFixture.findUsages(declaration).mapNotNull { it.file?.name }.sorted()
        )
    }

    /** Une déclaration sans usage le dit, plutôt que de n'afficher aucun inlay. */
    fun testDeclarationWithoutUsagesSaysSo() {
        val file = myFixture.configureByText(
            "lonely.rules",
            """
            Rule "Never referenced" On Policy.state {
                Assert true
            }
            """.trimIndent()
        )

        assertEquals("no usages", codeVisionHintOf(allOf<KrakenRuleDecl>(file).single()))
    }
}
