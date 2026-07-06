package com.kraken.plugin

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.documentation.KrakenDocumentationProvider
import com.kraken.plugin.inspection.KrakenDuplicateRuleInspection
import com.kraken.plugin.inspection.KrakenUnknownContextInspection
import com.kraken.plugin.inspection.KrakenUnusedRuleInspection
import com.kraken.plugin.psi.KrakenRuleDecl
import com.intellij.psi.util.PsiTreeUtil

class KrakenSmartFeaturesTest : BasePlatformTestCase() {

    fun testFieldCompletionAfterOnDot() {
        myFixture.configureByText(
            "test.rules",
            """
            Context Policy Is Base {
                String policyCd
                Child AddressInfo
            }

            Context Base {
                String inheritedField
            }

            Rule "r" On Policy.<caret>
            """.trimIndent()
        )
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue("Expected 'policyCd' in $strings", strings.contains("policyCd"))
        assertTrue("Expected child 'AddressInfo' in $strings", strings.contains("AddressInfo"))
        assertTrue("Expected inherited field in $strings", strings.contains("inheritedField"))
    }

    fun testFieldCompletionInWhenExpression() {
        myFixture.configureByText(
            "test.rules",
            """
            Context Policy {
                String policyCd
                String state
            }

            Rule "r" On Policy.state {
                When Policy.<caret>
            }
            """.trimIndent()
        )
        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings.orEmpty()
        assertTrue("Expected 'policyCd' in $strings", strings.contains("policyCd"))
    }

    fun testUnknownContextInspection() {
        myFixture.enableInspections(KrakenUnknownContextInspection())
        myFixture.configureByText(
            "test.rules",
            """
            Context Policy {
                String policyCd
            }

            Rule "r" On Nowhere.field {
                Assert true
            }
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Expected unknown context problem, got: ${highlights.map { it.description }}",
            highlights.any { it.description == "Unknown context 'Nowhere'" }
        )
    }

    fun testDuplicateRuleInspection() {
        myFixture.enableInspections(KrakenDuplicateRuleInspection())
        myFixture.configureByText(
            "test.rules",
            """
            Rule "Same" On Policy.a {
                Assert true
            }

            Rule "Same" On Policy.b {
                Assert false
            }

            @Dimension("state", "CA")
            Rule "Dimensioned" On Policy.c {
                Assert true
            }

            @Dimension("state", "NY")
            Rule "Dimensioned" On Policy.c {
                Assert false
            }
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        val duplicates = highlights.filter { it.description?.startsWith("Duplicate rule") == true }
        assertEquals("Only the two 'Same' rules should be flagged: $duplicates", 2, duplicates.size)
    }

    fun testUnusedRuleInspection() {
        myFixture.enableInspections(KrakenUnusedRuleInspection())
        myFixture.configureByText(
            "test.rules",
            """
            Rule "Used" On Policy.a {
                Assert true
            }

            Rule "Dead" On Policy.b {
                Assert false
            }

            EntryPoint "Validation" {
                "Used"
            }
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description == "Rule 'Dead' is not referenced by any entry point" })
        assertFalse(highlights.any { it.description == "Rule 'Used' is not referenced by any entry point" })
    }

    fun testQuickDocumentationForRule() {
        myFixture.configureByText(
            "test.rules",
            """
            Rule "Documented" On Policy.state {
                Description "Une règle documentée"
                Assert state != null
            }
            """.trimIndent()
        )
        val rule = PsiTreeUtil.findChildrenOfType(myFixture.file, KrakenRuleDecl::class.java).first()
        val doc = KrakenDocumentationProvider().generateDoc(rule, null)
        assertNotNull(doc)
        assertTrue("Doc should contain the name: $doc", doc!!.contains("Documented"))
        assertTrue("Doc should contain the description: $doc", doc.contains("Une règle documentée"))
    }

    fun testNamespaceScopedResolution() {
        myFixture.addFileToProject(
            "other.rules",
            """
            Namespace Other

            Rule "OtherRule" On Policy.x {
                Assert true
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "test.rules",
            """
            Namespace Mine

            EntryPoint "Validation" {
                "OtherRule<caret>"
            }
            """.trimIndent()
        )
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        // "Other" n'est pas inclus par "Mine" : la référence ne doit pas résoudre
        assertNull("Rule from a non-included namespace should not resolve", reference!!.resolve())
    }
}
