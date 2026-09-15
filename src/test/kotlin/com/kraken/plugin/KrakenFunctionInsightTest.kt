package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.documentation.KrakenDocumentationProvider
import com.kraken.plugin.documentation.KrakenFunctionDoc
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl

/**
 * Completion and documentation for KEL functions.
 *
 * [KrakenFunctionResolutionTest] covers resolution; this checks what the user actually
 * sees in the editor.
 */
class KrakenFunctionInsightTest : BasePlatformTestCase() {

    /**
     * The typed prefix filters the list, so each source is queried separately. When a
     * single candidate remains, the platform inserts it instead of opening the popup and
     * `lookupElementStrings` is null, so the resulting text is checked, parenthesis
     * included.
     */
    private fun completesInRuleBody(prefix: String, expected: String): Boolean {
        myFixture.configureByText(
            "completion.rules",
            """
            Function Limits(Coverage[] coverages) : Number[] {
                coverages.limitAmount
            }

            Rule "Uses functions" On Policy.limit {
                Assert $prefix<caret>
            }
            """.trimIndent(),
        )
        myFixture.completeBasic()
        return myFixture.lookupElementStrings?.contains(expected)
            ?: myFixture.file.text.contains("Assert $expected(")
    }

    fun testNativeFunctionsAreCompletedInRuleBodies() {
        assertTrue("Round is native", completesInRuleBody("Ro", "Round"))
    }

    fun testDeclaredFunctionsAreCompletedInRuleBodies() {
        assertTrue("Limits is declared in the file", completesInRuleBody("Li", "Limits"))
    }

    fun testFunctionsAreNotSuggestedInsideEntryPointBlocks() {
        myFixture.configureByText(
            "entrypoint.rules",
            """
            Rule "Some rule" On Policy.state {
                Assert true
            }

            EntryPoint "Validation" {
                <caret>
            }
            """.trimIndent(),
        )

        val suggestions = myFixture.completeBasic().map { it.lookupString }
        assertFalse(
            "An EntryPoint lists rules, not functions",
            suggestions.contains("Round"),
        )
        assertTrue(suggestions.contains("\"Some rule\""))
    }

    private fun docAtCall(): String? {
        val call = PsiTreeUtil.findChildOfType(myFixture.file, KrakenFunctionCall::class.java)
        return KrakenDocumentationProvider().generateDoc(call, null)
    }

    fun testQuickDocOnNativeCallComesFromTheCatalogue() {
        myFixture.configureByText(
            "doc.rules",
            """
            Rule "Rounds" On Policy.limit {
                Assert Round(1.5) > 0
            }
            """.trimIndent(),
        )

        val doc = docAtCall()
        assertNotNull(doc)
        assertTrue("Signature", doc!!.contains("Round(Number number) : Number"))
        assertTrue("Library of origin", doc.contains("Math (built-in)"))
        assertTrue("Documented example", doc.contains("Round(1.5)"))
    }

    fun testQuickDocOnDeclaredFunctionUsesItsDocComment() {
        myFixture.configureByText(
            "declared.rules",
            """
            /**
             * Limits of all coverages.
             * @since 1.2.0
             * @parameter coverages - coverages to iterate
             */
            Function Limits(Coverage[] coverages) : Number[] {
                coverages.limitAmount
            }
            """.trimIndent(),
        )

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, KrakenFunctionDecl::class.java)
        val doc = KrakenDocumentationProvider().generateDoc(declaration, null)
        assertNotNull(doc)
        assertTrue(doc!!.contains("Limits(Coverage[] coverages) : Number[]"))
        assertTrue(doc.contains("Limits of all coverages."))
        assertTrue(doc.contains("Since 1.2.0"))
        assertTrue(doc.contains("coverages to iterate"))
    }

    /** A bare signature must say there is no KEL body to look for. */
    fun testQuickDocFlagsSignatureOnlyFunctions() {
        myFixture.configureByText(
            "signature.rules",
            """
            Function GetPolicyCd(Policy) : String
            """.trimIndent(),
        )

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, KrakenFunctionDecl::class.java)
        val doc = KrakenDocumentationProvider().generateDoc(declaration, null)
        assertTrue(doc!!.contains("signature only, implemented in Java"))
    }

    fun testDocCommentTagsFollowTheEngineGrammar() {
        val parsed = KrakenFunctionDoc.parse(
            """
            /**
             * Description on
             * two lines.
             * @since 1.0.0
             * @example Limits(coverages)
             * @result {100, 200}
             * @parameter coverages - the coverages
             * @unknownTag ignored
             */
            """.trimIndent(),
        )

        assertEquals("Description on two lines.", parsed.description)
        assertEquals("1.0.0", parsed.since)
        assertEquals(listOf("coverages" to "the coverages"), parsed.parameters)
        assertEquals(listOf("Limits(coverages)" to "{100, 200}"), parsed.examples)
    }
}
