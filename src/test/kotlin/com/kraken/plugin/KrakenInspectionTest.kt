package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenRuleWithoutNameInspection
import com.kraken.plugin.inspection.KrakenUnresolvedRuleRefInspection

class KrakenInspectionTest : BasePlatformTestCase() {

    fun testRuleWithoutNameIsReported() {
        myFixture.enableInspections(KrakenRuleWithoutNameInspection())
        myFixture.configureByText(
            "test.rules",
            """
            Rule On Policy.state {
                Assert true
            }
            """.trimIndent(),
        )
        assertContainsElements(myFixture.problemDescriptions(), "[kvr001] Rule name is not defined.")
    }

    fun testNamedRuleIsNotReported() {
        myFixture.enableInspections(KrakenRuleWithoutNameInspection())
        myFixture.configureByText(
            "test.rules",
            """
            Rule "Named" On Policy.state {
                Assert true
            }
            """.trimIndent(),
        )
        assertDoesntContain(myFixture.problemDescriptions(), "[kvr001] Rule name is not defined.")
    }

    fun testUnresolvedRuleReferenceIsReported() {
        myFixture.enableInspections(KrakenUnresolvedRuleRefInspection())
        myFixture.configureByText(
            "test.rules",
            """
            Rule "Existing" On Policy.state {
                Assert true
            }

            EntryPoint "Validation" {
                "Missing"
            }
            """.trimIndent(),
        )
        assertContainsElements(myFixture.problemDescriptions(), "[kve005] Rule is included in entry point, but such rule does not exist: Missing.")
    }

    fun testResolvedRuleReferenceIsNotReported() {
        myFixture.enableInspections(KrakenUnresolvedRuleRefInspection())
        myFixture.configureByText(
            "test.rules",
            """
            Rule "Existing" On Policy.state {
                Assert true
            }

            EntryPoint "Validation" {
                "Existing"
            }
            """.trimIndent(),
        )
        assertDoesntContain(
            myFixture.problemDescriptions(),
            "[kve005] Rule is included in entry point, but such rule does not exist: Existing.",
        )
    }
}
