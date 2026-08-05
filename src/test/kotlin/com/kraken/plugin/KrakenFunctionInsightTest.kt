package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.documentation.KrakenDocumentationProvider
import com.kraken.plugin.documentation.KrakenFunctionDoc
import com.kraken.plugin.inspection.KrakenUnknownFunctionInspection
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl

/**
 * Complétion, documentation et inspection autour des fonctions KEL.
 *
 * [KrakenFunctionResolutionTest] couvre la résolution ; ici on vérifie ce que
 * l'utilisateur voit réellement dans l'éditeur.
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
    // Inspection
    // ------------------------------------------------------------------

    private fun highlightsFor(text: String): List<String> {
        myFixture.configureByText("inspect.rules", text)
        myFixture.enableInspections(KrakenUnknownFunctionInspection())
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("function", ignoreCase = true) }
    }

    fun testNativeAndDeclaredCallsAreNotReported() {
        val problems = highlightsFor(
            """
            Function Plan(PackageDetails details) : String {
                details.planCd
            }

            Rule "Valid calls" On Policy.state {
                Assert Round(Sum(coverages.limitAmount), 2) > 0 and Plan(details) != null
            }
            """.trimIndent()
        )
        assertEquals("Aucun appel inconnu", emptyList<String>(), problems)
    }

    /** Une signature nue est une déclaration valide : elle ne doit rien déclencher. */
    fun testSignatureWithoutBodySatisfiesTheInspection() {
        val problems = highlightsFor(
            """
            Function GetPolicyCd(Policy) : String

            Rule "Calls a Java function" On Policy.state {
                Assert GetPolicyCd(policy) != null
            }
            """.trimIndent()
        )
        assertEquals(emptyList<String>(), problems)
    }

    fun testUnknownNameIsReported() {
        val problems = highlightsFor(
            """
            Rule "Typo" On Policy.state {
                Assert Rnd(1.5) > 0
            }
            """.trimIndent()
        )
        assertEquals(listOf("Unknown function 'Rnd'"), problems)
    }

    /** Le nom existe mais pas à cette arité : le message doit le dire. */
    fun testWrongArityReportsTheDeclaredOnes() {
        val problems = highlightsFor(
            """
            Rule "Too many arguments" On Policy.state {
                Assert Round(1.5, 2, 3) > 0
            }
            """.trimIndent()
        )
        assertEquals(
            listOf("Function 'Round' with 3 parameter(s) does not exist (declared with 1 or 2)"),
            problems
        )
    }

    /**
     * Un projet peut embarquer sa propre `FunctionLibrary` annotée `@Native` :
     * le moteur la met en portée sans qu'aucune signature ne soit déclarée, et
     * une analyse statique ne peut pas la découvrir. L'option évite que ces
     * appels parfaitement valides soient signalés.
     */
    fun testProjectNativeFunctionsCanBeDeclaredInTheOptions() {
        val inspection = KrakenUnknownFunctionInspection()
        inspection.additionalNativeFunctions = mutableListOf("ResolveTerritory")

        myFixture.configureByText(
            "custom.rules",
            """
            Rule "Uses a project native" On Policy.state {
                Assert ResolveTerritory(Policy.address, 2) != null
            }
            """.trimIndent()
        )
        myFixture.enableInspections(inspection)

        val problems = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("function", ignoreCase = true) }
        assertEquals("Le nom listé dans les options n'est plus signalé", emptyList<String>(), problems)
    }

    fun testFunctionFromAnInvisibleNamespaceIsReported() {
        myFixture.addFileToProject(
            "library.rules",
            """
            Namespace Library

            Function Hidden(Policy p) : String {
                p.policyCd
            }
            """.trimIndent()
        )
        val problems = highlightsFor(
            """
            Namespace Consumer

            Rule "Cannot see it" On Policy.state {
                Assert Hidden(policy) != null
            }
            """.trimIndent()
        )
        assertEquals(listOf("Unknown function 'Hidden'"), problems)
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
