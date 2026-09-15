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
 * Ctrl+B targets, in both directions.
 *
 * [KrakenNavigationPresentationTest] covers popup labels; this checks the targets
 * themselves, and above all that none is missing: offering one implementation where
 * several are legitimate picks one by index order.
 */
class KrakenBidirectionalNavigationTest : BasePlatformTestCase() {

    /** Targets Ctrl+B would produce on the name carried by [element]. */
    private fun targetsFor(element: PsiElement): List<PsiElement> {
        val offset = element.textOffset
        val leaf = element.containingFile.findElementAt(offset)
        assertNotNull("Expected a leaf at offset $offset", leaf)
        return KrakenGotoDeclarationHandler()
            .getGotoDeclarationTargets(leaf, offset, myFixture.editor)
            .orEmpty()
            .toList()
    }

    private fun labelsOf(targets: List<PsiElement>): List<String> = targets.map { (it as NavigationItem).presentation }
        .map { "${it?.presentableText} @ ${it?.locationString}" }
        .sorted()

    private inline fun <reified T : PsiElement> allOf(file: PsiElement): List<T> = PsiTreeUtil.findChildrenOfType(file, T::class.java).toList()

    /** Each EntryPoint item leads to its own rule, not to the first one found. */
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
            """.trimIndent(),
        )

        val declarations = allOf<KrakenRuleDecl>(file).associateBy { it.name }
        assertEquals(3, declarations.size)

        allOf<KrakenRuleRef>(file).forEach { reference ->
            val target = targetsFor(reference).singleOrNull()
            assertNotNull("No target for \"${reference.ruleName}\"", target)
            assertSame(
                "\"${reference.ruleName}\" must land on its own declaration",
                declarations[reference.ruleName],
                target,
            )
        }
    }

    /**
     * Two independent namespaces declare the same rule. Visibility forbids crossing, so
     * each EntryPoint stays within its own namespace.
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
            """.trimIndent(),
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
            """.trimIndent(),
        )

        listOf(base, other).forEach { file ->
            val target = targetsFor(allOf<KrakenRuleRef>(file).single()).single()
            assertEquals(
                "Reference must resolve inside its own namespace",
                file.name,
                target.containingFile.name,
            )
        }
    }

    /**
     * `@Dimension` variants: same name, same file, two valid implementations. Ctrl+B must
     * offer both; this is the case KrakenDuplicateRuleInspection deliberately allows.
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
            """.trimIndent(),
        )

        val targets = targetsFor(allOf<KrakenRuleRef>(file).single())
        assertEquals("Both dimensional variants are valid targets", 2, targets.size)

        // Same name, same file: without the annotation the popup would show two identical lines.
        assertEquals(
            listOf(
                "\"Coverage limit\" @Dimension(\"plan\", \"GOLD\") @ dimensioned.rules",
                "\"Coverage limit\" @Dimension(\"plan\", \"SILVER\") @ dimensioned.rules",
            ),
            labelsOf(targets),
        )
    }

    /** `Import Rule` bypasses Include: the target stays in the source namespace. */
    fun testImportedRuleResolvesToItsSourceNamespace() {
        myFixture.addFileToProject(
            "library.rules",
            """
            Namespace Library

            Rule "Imported rule" On Policy.state {
                Assert true
            }
            """.trimIndent(),
        )
        val consumer = myFixture.configureByText(
            "consumer.rules",
            """
            Namespace Consumer
            Import Rule "Imported rule" From Library

            EntryPoint "Validation" {
                "Imported rule"
            }
            """.trimIndent(),
        )

        val target = targetsFor(allOf<KrakenRuleRef>(consumer).single()).single()
        assertEquals("library.rules", target.containingFile.name)
    }

    /** Nested `EntryPoint "x"` item: same requirement as for rules. */
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
            """.trimIndent(),
        )

        val target = targetsFor(allOf<KrakenEpRef>(file).single()).single()
        assertSame(allOf<KrakenEntryPointDecl>(file).first { it.name == "Reused" }, target)
    }

    /**
     * Declaration → usages goes through the platform's usages popup, fed by
     * `ReferencesSearch`, and the "N usages" inlay counts the same thing.
     */
    private fun usageCountOf(declaration: PsiElement): Int = myFixture.findUsages(declaration as com.intellij.psi.PsiNamedElement).size

    private fun codeVisionHintOf(declaration: PsiElement): String? = KrakenReferencesCodeVisionProvider().getHint(declaration, declaration.containingFile)

    /** A rule shared by several EntryPoints: all of them are usages. */
    fun testImplementationIsUsedByEveryEntryPointReferencingIt() {
        myFixture.addFileToProject(
            "billing.rules",
            """
            EntryPoint "Billing" {
                "Shared rule"
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "quoting.rules",
            """
            EntryPoint "Quoting" {
                "Shared rule"
            }
            """.trimIndent(),
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
            """.trimIndent(),
        )

        val declaration = allOf<KrakenRuleDecl>(file).single()
        assertEquals(3, usageCountOf(declaration))
        assertEquals("3 usages", codeVisionHintOf(declaration))
        assertEquals(
            listOf("billing.rules", "quoting.rules", "rules.rules"),
            myFixture.findUsages(declaration).mapNotNull { it.file?.name }.sorted(),
        )
    }

    /**
     * An EntryPoint in a namespace that cannot see the declaration is not a usage, even with
     * the same name: it resolves elsewhere.
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
            """.trimIndent(),
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
            """.trimIndent(),
        )

        val declaration = allOf<KrakenRuleDecl>(base).single()
        assertEquals("1 usage", codeVisionHintOf(declaration))
        assertEquals(
            listOf("base.rules"),
            myFixture.findUsages(declaration).mapNotNull { it.file?.name },
        )
    }

    /** An EntryPoint reused by several others counts every caller. */
    fun testEntryPointDeclarationCountsEveryCaller() {
        myFixture.addFileToProject(
            "first.rules",
            """
            EntryPoint "First" {
                EntryPoint "Reused"
            }
            """.trimIndent(),
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
            """.trimIndent(),
        )

        val declaration = allOf<KrakenEntryPointDecl>(file).first { it.name == "Reused" }
        assertEquals("2 usages", codeVisionHintOf(declaration))
        assertEquals(
            listOf("base.rules", "first.rules"),
            myFixture.findUsages(declaration).mapNotNull { it.file?.name }.sorted(),
        )
    }

    /** A declaration without usages says so instead of showing no inlay. */
    fun testDeclarationWithoutUsagesSaysSo() {
        val file = myFixture.configureByText(
            "lonely.rules",
            """
            Rule "Never referenced" On Policy.state {
                Assert true
            }
            """.trimIndent(),
        )

        assertEquals("no usages", codeVisionHintOf(allOf<KrakenRuleDecl>(file).single()))
    }
}
