package com.kraken.plugin

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenPathSegment
import com.kraken.plugin.psi.KrakenRefExpr

/**
 * Ctrl+B depuis une expression : `Assert`, `When`, `Default To`.
 *
 * [KrakenScopeResolverTest] couvre la sémantique de portée ; ici on vérifie
 * qu'elle est bien câblée en références PSI, y compris là où elle doit
 * renoncer — une référence qui ne résout pas ne navigue simplement pas.
 */
class KrakenExpressionNavigationTest : BasePlatformTestCase() {

    private val model = """
        Root Context Policy {
            String policyCd
            Child AddressInfo
            Child Coverage
        }

        Context AddressInfo {
            String postalCode
            Child Country
        }

        Context Country {
            String isoCode
        }

        Context Coverage {
            Money limitAmount
        }
    """.trimIndent()

    private fun configureRule(body: String) = myFixture.configureByText(
        "expr.rules",
        """
        $model

        Rule "Under test" On Policy.policyCd {
            $body
        }
        """.trimIndent()
    )

    private fun ref(name: String): KrakenRefExpr =
        PsiTreeUtil.collectElementsOfType(myFixture.file, KrakenRefExpr::class.java)
            .first { it.referenceName == name }

    private fun segment(name: String): KrakenPathSegment =
        PsiTreeUtil.collectElementsOfType(myFixture.file, KrakenPathSegment::class.java)
            .first { it.segmentName == name }

    private fun assertResolvesTo(target: PsiElement?, expectedText: String) {
        assertNotNull("Expected the reference to resolve", target)
        assertTrue(
            "Resolved to \"${target!!.text.trim()}\", expected something containing \"$expectedText\"",
            target.text.contains(expectedText)
        )
    }

    // ------------------------------------------------------------------
    // Identifiants nus
    // ------------------------------------------------------------------

    fun testBareFieldNavigatesToItsDeclaration() {
        configureRule("Assert policyCd != null")
        assertResolvesTo(ref("policyCd").reference?.resolve(), "String policyCd")
    }

    fun testBareContextNavigatesToItsDeclaration() {
        configureRule("Assert Coverage != null")
        val target = ref("Coverage").reference?.resolve()
        assertNotNull(target)
        // Coverage est un Child de Policy : la portée locale gagne, comme dans
        // le moteur, donc on atterrit sur l'enfant et non sur le Context.
        assertEquals(KrakenTypes.CHILD_DECL, target!!.node.elementType)
    }

    fun testVariableNavigatesToItsDeclarationSite() {
        configureRule("Assert every item in coverages satisfies item != null")
        assertResolvesTo(ref("item").reference?.resolve(), "item")
    }

    fun testUnknownIdentifierDoesNotNavigate() {
        configureRule("Assert whatIsThis != null")
        assertNull(ref("whatIsThis").reference?.resolve())
    }

    // ------------------------------------------------------------------
    // Chaînes d'accès
    // ------------------------------------------------------------------

    fun testFieldOfAChildContextResolves() {
        configureRule("Assert AddressInfo.postalCode != null")
        assertResolvesTo(segment("postalCode").reference?.resolve(), "String postalCode")
    }

    /** La chaîne se poursuit tant que chaque maillon désigne un contexte. */
    fun testTwoLevelChainResolves() {
        configureRule("Assert AddressInfo.Country.isoCode != null")
        assertResolvesTo(segment("isoCode").reference?.resolve(), "String isoCode")
    }

    fun testUnknownFieldInAKnownContextDoesNotResolve() {
        configureRule("Assert AddressInfo.notAField != null")
        assertNull(segment("notAField").reference?.resolve())
    }

    /**
     * La tête est un champ scalaire : son type n'est pas un contexte, donc la
     * chaîne s'arrête. Sans inférence de types on ne devine pas — c'est le
     * comportement voulu, pas une lacune accidentelle.
     */
    fun testChainOnAScalarHeadStopsResolving() {
        configureRule("Assert policyCd.something != null")
        assertNull(segment("something").reference?.resolve())
    }

    /** Un segment suivi de parenthèses est un appel, pas un champ. */
    fun testMethodCallSegmentIsNotAFieldReference() {
        configureRule("Assert AddressInfo.postalCode.Trim() != null")
        assertNull(segment("Trim").reference)
    }

    // ------------------------------------------------------------------
    // Complétion
    // ------------------------------------------------------------------

    fun testCompletionAfterADotUsesTheResolvedContext() {
        myFixture.configureByText(
            "complete.rules",
            """
            $model

            Rule "Under test" On Policy.policyCd {
                Assert AddressInfo.<caret>
            }
            """.trimIndent()
        )
        val suggestions = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertTrue("champ du contexte résolu", suggestions.contains("postalCode"))
        assertFalse("pas les champs d'un autre contexte", suggestions.contains("limitAmount"))
    }
}
