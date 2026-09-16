package com.kraken.plugin

import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Spellchecking.
 *
 * Error messages in a `.rules` file end up in front of an application's end users. The
 * negative tests matter as much: business vocabulary flagged by mistake would bury the
 * real typos.
 */
class KrakenSpellcheckTest : BasePlatformTestCase() {

    private fun typos(source: String): List<String> {
        myFixture.configureByText("spell.rules", source)
        myFixture.enableInspections(SpellCheckingInspection())
        return myFixture.doHighlighting()
            .filter { it.description?.contains("Typo", ignoreCase = true) == true }
            .map { myFixture.file.text.substring(it.startOffset, it.endOffset) }
    }

    fun testATypoInAnErrorMessageIsReported() {
        assertTrue(
            "the typo must be reported",
            typos(
                """
                Rule "R" On Policy.state {
                    Assert true
                    Error "code" : "Limit amount is mandatoryy"
                }
                """.trimIndent(),
            ).contains("mandatoryy"),
        )
    }

    fun testATypoInACommentIsReported() {
        assertTrue(
            typos(
                """
                // this rule is deliberatelly wrong
                Rule "R" On Policy.state {
                    Assert true
                }
                """.trimIndent(),
            ).contains("deliberatelly"),
        )
    }

    fun testACleanMessageIsNotReported() {
        assertEquals(
            emptyList<String>(),
            typos(
                """
                Rule "R" On Policy.state {
                    Assert true
                    Error "code" : "Limit amount is mandatory"
                }
                """.trimIndent(),
            ),
        )
    }

    /** `Error "code" : "message"`: the code is an identifier, so only the second string is checked. */
    fun testTheErrorCodeIsNotCheckedButTheMessageIs() {
        val reported = typos(
            """
            Rule "R" On Policy.state {
                Assert true
                Error "limitAmountMandatoryy" : "Limit amount is requiredd"
            }
            """.trimIndent(),
        )
        assertTrue("the message must be checked: $reported", reported.contains("requiredd"))
        assertFalse("the code must not be: $reported", reported.contains("Mandatoryy"))
    }

    /**
     * Business vocabulary such as `policyCd` or `AutoCOMPCoverage` is in no dictionary;
     * flagging it would make the check unusable.
     */
    fun testIdentifiersAndRuleNamesAreNotChecked() {
        assertEquals(
            emptyList<String>(),
            typos(
                """
                Context AutoCOMPCoverage {
                    String policyCd
                }

                Rule "AZStateCoverateVisibility" On AutoCOMPCoverage.policyCd {
                    Assert policyCd != null
                }
                """.trimIndent(),
            ),
        )
    }
}
