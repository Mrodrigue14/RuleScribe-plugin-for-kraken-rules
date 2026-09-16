package com.kraken.plugin

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenPathSegment
import com.kraken.plugin.psi.KrakenRefExpr

/**
 * Ctrl+B from an expression: `Assert`, `When`, `Default To`.
 *
 * [KrakenScopeResolverTest] covers scope semantics; this checks that they are wired into
 * PSI references, including where they give up: a reference that does not resolve
 * simply does not navigate.
 */
class KrakenExpressionNavigationTest : KrakenRuleBodyTestCase() {

    override val model = """
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

    private fun segment(name: String): KrakenPathSegment = PsiTreeUtil.collectElementsOfType(myFixture.file, KrakenPathSegment::class.java)
        .first { it.segmentName == name }

    private fun assertResolvesTo(target: PsiElement?, expectedText: String) {
        assertNotNull("Expected the reference to resolve", target)
        assertTrue(
            "Resolved to \"${target!!.text.trim()}\", expected something containing \"$expectedText\"",
            target.text.contains(expectedText),
        )
    }

    fun testBareFieldNavigatesToItsDeclaration() {
        configureRule("Assert policyCd != null")
        assertResolvesTo(refNamed("policyCd").reference?.resolve(), "String policyCd")
    }

    fun testBareContextNavigatesToItsDeclaration() {
        configureRule("Assert Coverage != null")
        val target = refNamed("Coverage").reference?.resolve()
        assertNotNull(target)
        // Coverage is a Child of Policy: the local scope wins, as in the engine, so this lands
        // on the child and not on the Context.
        assertEquals(KrakenTypes.CHILD_DECL, target!!.node.elementType)
    }

    fun testVariableNavigatesToItsDeclarationSite() {
        configureRule("Assert every item in coverages satisfies item != null")
        assertResolvesTo(refNamed("item").reference?.resolve(), "item")
    }

    fun testUnknownIdentifierDoesNotNavigate() {
        configureRule("Assert whatIsThis != null")
        assertNull(refNamed("whatIsThis").reference?.resolve())
    }

    fun testFieldOfAChildContextResolves() {
        configureRule("Assert AddressInfo.postalCode != null")
        assertResolvesTo(segment("postalCode").reference?.resolve(), "String postalCode")
    }

    /** The chain continues while each link denotes a context. */
    fun testTwoLevelChainResolves() {
        configureRule("Assert AddressInfo.Country.isoCode != null")
        assertResolvesTo(segment("isoCode").reference?.resolve(), "String isoCode")
    }

    fun testUnknownFieldInAKnownContextDoesNotResolve() {
        configureRule("Assert AddressInfo.notAField != null")
        assertNull(segment("notAField").reference?.resolve())
    }

    /**
     * The head is a scalar field whose type is not a context, so the chain stops. Without
     * type inference nothing is guessed, by design.
     */
    fun testChainOnAScalarHeadStopsResolving() {
        configureRule("Assert policyCd.something != null")
        assertNull(segment("something").reference?.resolve())
    }

    /** A segment followed by parentheses is a call, not a field. */
    fun testMethodCallSegmentIsNotAFieldReference() {
        configureRule("Assert AddressInfo.postalCode.Trim() != null")
        assertNull(segment("Trim").reference)
    }

    fun testCompletionAfterADotUsesTheResolvedContext() {
        myFixture.configureByText(
            "complete.rules",
            """
            $model

            Rule "Under test" On Policy.policyCd {
                Assert AddressInfo.<caret>
            }
            """.trimIndent(),
        )
        val suggestions = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertTrue("field of the resolved context", suggestions.contains("postalCode"))
        assertFalse("not the fields of another context", suggestions.contains("limitAmount"))
    }
}
