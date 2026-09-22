package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.navigation.KrakenVcsCodeVisionContext
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Which elements get the "author, date" inlay, and how far their block extends.
 *
 * The inlay itself needs a VCS repository and annotations, out of reach of a headless
 * test. What is testable, and owned by the plugin, is element selection and recognising
 * the closing brace that the extent depends on.
 */
class KrakenVcsCodeVisionTest : BasePlatformTestCase() {

    private val context = KrakenVcsCodeVisionContext()

    private fun configure() = myFixture.configureByText(
        "annotated.rules",
        """
        Namespace Policy

        Context Coverage {
            Money limitAmount
        }

        Function TotalLimit(Coverage[] coverages) : Number {
            Sum(coverages.limitAmount)
        }

        Rule "Limit is positive" On Coverage.limitAmount {
            Assert limitAmount > 0
        }

        EntryPoint "Validation" {
            "Limit is positive"
        }
        """.trimIndent(),
    )

    private fun accepted(): List<String> {
        val found = mutableListOf<String>()
        PsiTreeUtil.processElements(myFixture.file) { element ->
            if (context.isAccepted(element)) found += element.node.elementType.toString()
            true
        }
        return found.sorted()
    }

    fun testEveryTopLevelDeclarationIsAccepted() {
        configure()
        assertEquals(
            listOf("CONTEXT_DECL", "ENTRY_POINT_DECL", "FUNCTION_DECL", "RULE_DECL"),
            accepted(),
        )
    }

    /** References and bodies are not declarations: no inlay. */
    fun testNonDeclarationsAreRejected() {
        configure()
        val rejected = listOf(
            KrakenTypes.RULE_REF,
            KrakenTypes.RULE_BODY,
            KrakenTypes.FUNCTION_CALL,
            KrakenTypes.DIMENSION_DECL,
        )
        PsiTreeUtil.processElements(myFixture.file) { element ->
            if (element.node.elementType in rejected) {
                assertFalse(
                    "${element.node.elementType} must not carry an author inlay",
                    context.isAccepted(element),
                )
            }
            true
        }
    }

    /**
     * The base class derives the block extent from the closing brace. If it were not
     * recognised, the extent would stop early and the inlay would show the author of the
     * wrong part of the file. The result is checked because the predicate is protected.
     */
    fun testEffectiveRangeCoversTheWholeDeclaration() {
        val file = configure()
        val rule = PsiTreeUtil.findChildrenOfType(file, KrakenRuleDecl::class.java).single()

        val range = context.computeEffectiveRange(rule)
        // The base starts at `textOffset`, which KrakenRuleDecl points at the name, so the inlay
        // shows the rule's author and not that of a preceding @Dimension annotation.
        assertEquals("Starts at the rule name", rule.nameIdentifier!!.textOffset, range.startOffset)
        // The base stops at the end of the body's last line, excluding the closing brace line;
        // otherwise the author shown would often be whoever added the last statement.
        assertTrue("The range stays within the rule", rule.textRange.contains(range))
        assertTrue(
            "The range must cover the whole rule body",
            file.text.substring(range.startOffset, range.endOffset).contains("Assert limitAmount > 0"),
        )
    }
}
