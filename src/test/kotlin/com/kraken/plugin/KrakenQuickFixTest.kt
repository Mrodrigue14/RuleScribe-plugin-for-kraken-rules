package com.kraken.plugin

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenDuplicateRuleInspection
import com.kraken.plugin.inspection.KrakenUndeclaredDimensionInspection

/**
 * Inspection quick fixes.
 *
 * The resulting text is checked, not just that a fix is offered: it must stay valid
 * Kraken, including the order required by
 * `kraken_file ::= namespace_decl? import_decl* model_item*`.
 */
class KrakenQuickFixTest : BasePlatformTestCase() {

    /**
     * The platform wraps a `LocalQuickFix` in an action whose `familyName` carries the
     * displayed label (`getName()` when the fix defines one), so fixes are looked up by
     * prefix.
     */
    private fun applyFix(fileName: String, before: String, label: String): String {
        myFixture.configureByText(fileName, before)
        val fix = myFixture.getAllQuickFixes().firstOrNull { it.familyName.startsWith(label) }
            ?: error(
                "correctif '$label' absent, disponibles: " +
                    myFixture.getAllQuickFixes().map { it.familyName },
            )
        myFixture.launchAction(fix)
        val after = myFixture.file.text
        // The fix writes text, so what matters is that the file still parses.
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        assertEquals(
            "the fix broke the file:\n$after",
            emptyList<String>(),
            errors.map { it.errorDescription },
        )
        return after
    }

    fun testDeclaresMissingDimensionAfterTheExistingOnes() {
        myFixture.enableInspections(KrakenUndeclaredDimensionInspection())
        val after = applyFix(
            "dims.rules",
            """
            Dimension "planCd" : String

            @Dimension("packageCd", "Pizza")
            Rule "R" On Policy.state {
                Assert true
            }
            """.trimIndent(),
            "Declare dimension",
        )
        assertTrue(
            "dimensions stay grouped, one per line:\n$after",
            after.contains("Dimension \"planCd\" : String\nDimension \"packageCd\" : String"),
        )
    }

    /**
     * A dimension cannot precede `Namespace`: the fix must insert after the header, or the
     * file no longer parses.
     */
    fun testDeclaredDimensionGoesAfterTheHeader() {
        myFixture.enableInspections(KrakenUndeclaredDimensionInspection())
        val after = applyFix(
            "ns.rules",
            """
            Namespace Policy
            Include Base

            Dimension "planCd" : String

            @Dimension("packageCd", "Pizza")
            Rule "R" On Policy.state {
                Assert true
            }
            """.trimIndent(),
            "Declare dimension",
        )
        val namespaceAt = after.indexOf("Namespace Policy")
        val newDimensionAt = after.indexOf("Dimension \"packageCd\"")
        assertTrue("the dimension must follow the Namespace:\n$after", namespaceAt < newDimensionAt)
        assertTrue(after.indexOf("Include Base") < newDimensionAt)
    }

    fun testAddsDimensionAnnotationToADuplicateRule() {
        myFixture.enableInspections(KrakenDuplicateRuleInspection())
        val after = applyFix(
            "dup.rules",
            """
            Rule "Same" On Policy.state {
                Assert true
            }

            Rule "Same" On Policy.state {
                Assert false
            }
            """.trimIndent(),
            "Add a differentiating @Dimension annotation",
        )
        assertTrue(
            "the annotation precedes the rule:\n$after",
            after.contains("@Dimension(\"dimensionName\", \"value\")\nRule \"Same\""),
        )
    }

    /** A rule nested in `Rules { }` keeps its indentation. */
    fun testAnnotationFollowsTheRuleIndentation() {
        myFixture.enableInspections(KrakenDuplicateRuleInspection())
        val after = applyFix(
            "nested.rules",
            """
            Rules {
                Rule "Same" On Policy.state {
                    Assert true
                }

                Rule "Same" On Policy.state {
                    Assert false
                }
            }
            """.trimIndent(),
            "Add a differentiating @Dimension annotation",
        )
        assertTrue(
            "the annotation uses the rule's indentation:\n$after",
            after.contains("    @Dimension(\"dimensionName\", \"value\")\n    Rule \"Same\""),
        )
    }
}
