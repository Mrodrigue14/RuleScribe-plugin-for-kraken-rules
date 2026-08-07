package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.documentation.KrakenDocumentationProvider
import com.kraken.plugin.documentation.KrakenFunctionDoc
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl

/**
 * Complétion et documentation autour des fonctions KEL.
 *
 * [KrakenFunctionResolutionTest] couvre la résolution ; ici on vérifie ce que
 * l'utilisateur voit réellement dans l'éditeur. Il n'y a plus d'inspection
 * « unknown function » (voir ROADMAP.md : nécessiterait un projet propriétaire
 * pour être vérifiée fiablement contre de vraies bibliothèques Java).
 */
class KrakenFunctionInsightTest : BasePlatformTestCase() {

    // ------------------------------------------------------------------
    // Complétion
    // ------------------------------------------------------------------

    /**
     * Le préfixe frappé filtre la liste, on interroge donc chaque source
     * séparément. Quand un seul candidat subsiste, la plateforme l'insère au
     * lieu d'ouvrir la popup et `lookupElementStrings` vaut null : on vérifie
     * alors le texte obtenu, parenthèse comprise.
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
            """.trimIndent()
        )
        myFixture.completeBasic()
        return myFixture.lookupElementStrings?.contains(expected)
            ?: myFixture.file.text.contains("Assert $expected(")
    }

    fun testNativeFunctionsAreCompletedInRuleBodies() {
        assertTrue("Round est une native", completesInRuleBody("Ro", "Round"))
    }

    fun testDeclaredFunctionsAreCompletedInRuleBodies() {
        assertTrue("Limits est déclarée dans le fichier", completesInRuleBody("Li", "Limits"))
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
            """.trimIndent()
        )

        val suggestions = myFixture.completeBasic().map { it.lookupString }
        assertFalse(
            "Un EntryPoint liste des règles, pas des fonctions",
            suggestions.contains("Round")
        )
        assertTrue(suggestions.contains("\"Some rule\""))
    }

    // ------------------------------------------------------------------
    // Documentation
    // ------------------------------------------------------------------

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
            """.trimIndent()
        )

        val doc = docAtCall()
        assertNotNull(doc)
        assertTrue("Signature", doc!!.contains("Round(Number number) : Number"))
        assertTrue("Bibliothèque d'origine", doc.contains("Math (built-in)"))
        assertTrue("Exemple documenté", doc.contains("Round(1.5)"))
    }

    fun testQuickDocOnDeclaredFunctionUsesItsDocComment() {
        myFixture.configureByText(
            "declared.rules",
            """
            /**
             * Limites de toutes les garanties.
             * @since 1.2.0
             * @parameter coverages - garanties à parcourir
             */
            Function Limits(Coverage[] coverages) : Number[] {
                coverages.limitAmount
            }
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, KrakenFunctionDecl::class.java)
        val doc = KrakenDocumentationProvider().generateDoc(declaration, null)
        assertNotNull(doc)
        assertTrue(doc!!.contains("Limits(Coverage[] coverages) : Number[]"))
        assertTrue(doc.contains("Limites de toutes les garanties."))
        assertTrue(doc.contains("Since 1.2.0"))
        assertTrue(doc.contains("garanties à parcourir"))
    }

    /** Une signature nue doit annoncer qu'il n'y a pas de corps KEL à chercher. */
    fun testQuickDocFlagsSignatureOnlyFunctions() {
        myFixture.configureByText(
            "signature.rules",
            """
            Function GetPolicyCd(Policy) : String
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, KrakenFunctionDecl::class.java)
        val doc = KrakenDocumentationProvider().generateDoc(declaration, null)
        assertTrue(doc!!.contains("signature only, implemented in Java"))
    }

    fun testDocCommentTagsFollowTheEngineGrammar() {
        val parsed = KrakenFunctionDoc.parse(
            """
            /**
             * Description sur
             * deux lignes.
             * @since 1.0.0
             * @example Limits(coverages)
             * @result {100, 200}
             * @parameter coverages - les garanties
             * @unknownTag ignoré
             */
            """.trimIndent()
        )

        assertEquals("Description sur deux lignes.", parsed.description)
        assertEquals("1.0.0", parsed.since)
        assertEquals(listOf("coverages" to "les garanties"), parsed.parameters)
        assertEquals(listOf("Limits(coverages)" to "{100, 200}"), parsed.examples)
    }
}
