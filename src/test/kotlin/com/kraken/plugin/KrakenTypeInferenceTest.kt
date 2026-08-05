package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenRefExpr
import com.kraken.plugin.types.KrakenType
import com.kraken.plugin.types.KrakenTypeInference

/**
 * Inférence de types KEL.
 *
 * Ce qui compte autant que les types déduits, c'est [KrakenType.Unknown] :
 * il marque ce que le plugin ne sait pas typer, et c'est lui qui fait
 * s'abstenir les vérifications. Un Unknown rendu là où un type est attendu
 * est une occasion manquée ; un type inventé est un faux diagnostic.
 */
class KrakenTypeInferenceTest : BasePlatformTestCase() {

    private val model = """
        Root Context Policy {
            String policyCd
            Money limitAmount
            Decimal premium
            Integer termNo
            Date effectiveDate
            DateTime createdOn
            Boolean active
            Child AddressInfo
            Child* Coverage
        }

        Context AddressInfo {
            String postalCode
        }

        Context Coverage {
            Money limit
        }
    """.trimIndent()

    private fun configureRule(body: String) = myFixture.configureByText(
        "types.rules",
        """
        $model

        Rule "Under test" On Policy.policyCd {
            $body
        }
        """.trimIndent()
    )

    private fun typeOfRef(name: String): KrakenType =
        KrakenTypeInference.typeOf(
            PsiTreeUtil.collectElementsOfType(myFixture.file, KrakenRefExpr::class.java)
                .first { it.referenceName == name }
        )

    // ------------------------------------------------------------------
    // Champs de contexte
    // ------------------------------------------------------------------

    fun testPrimitiveFieldTypes() {
        configureRule("Assert policyCd != null")
        assertEquals(KrakenType.String, typeOfRef("policyCd"))
    }

    /** `Integer` et `Decimal` du DSL sont tous deux des `Number` en KEL. */
    fun testIntegerAndDecimalCollapseToNumber() {
        configureRule("Assert premium > 0 and termNo > 0")
        assertEquals(KrakenType.Number, typeOfRef("premium"))
        assertEquals(KrakenType.Number, typeOfRef("termNo"))
    }

    fun testMoneyStaysDistinctFromNumber() {
        configureRule("Assert limitAmount > 0")
        assertEquals(KrakenType.Money, typeOfRef("limitAmount"))
    }

    fun testDateAndDateTimeAreDistinct() {
        configureRule("Assert effectiveDate != null and createdOn != null")
        assertEquals(KrakenType.Date, typeOfRef("effectiveDate"))
        assertEquals(KrakenType.DateTime, typeOfRef("createdOn"))
    }

    fun testChildIsAContextAndStarMakesItACollection() {
        configureRule("Assert AddressInfo != null and Coverage != null")
        assertEquals(KrakenType.Context("AddressInfo"), typeOfRef("AddressInfo"))
        assertEquals(KrakenType.Array(KrakenType.Context("Coverage")), typeOfRef("Coverage"))
    }

    // ------------------------------------------------------------------
    // Appels de fonction
    // ------------------------------------------------------------------

    fun testNativeFunctionReturnTypeComesFromTheCatalogue() {
        configureRule("Assert Today() != null")
        val call = PsiTreeUtil.findChildOfType(myFixture.file, KrakenFunctionCall::class.java)!!
        assertEquals(KrakenType.Date, KrakenTypeInference.typeOf(call))
    }

    fun testDeclaredFunctionReturnTypeIsUsed() {
        myFixture.configureByText(
            "declared.rules",
            """
            $model

            Function Total(Coverage[] items) : Money {
                Sum(items.limit)
            }

            Rule "Uses it" On Policy.policyCd {
                Assert Total(Coverage) > 0
            }
            """.trimIndent()
        )
        val call = PsiTreeUtil.collectElementsOfType(myFixture.file, KrakenFunctionCall::class.java)
            .first { it.functionName == "Total" }
        assertEquals(KrakenType.Money, KrakenTypeInference.typeOf(call))
    }

    // ------------------------------------------------------------------
    // Là où l'inférence doit renoncer
    // ------------------------------------------------------------------

    /** Une union `Date | DateTime` n'est pas modélisée : dynamique, pas faux. */
    fun testUnionParameterTypeBecomesAny() {
        assertEquals(KrakenType.Any, KrakenType.fromDslName("Date | DateTime"))
    }

    fun testGenericTypeBecomesAny() {
        assertEquals(KrakenType.Any, KrakenType.fromDslName("<T>"))
        assertEquals(KrakenType.Array(KrakenType.Any), KrakenType.fromDslName("<T>[]"))
    }

    fun testUnresolvedReferenceHasNoType() {
        configureRule("Assert whatIsThis != null")
        assertEquals(KrakenType.Unknown, typeOfRef("whatIsThis"))
    }

    // ------------------------------------------------------------------
    // Règles d'assignabilité et de comparabilité
    // ------------------------------------------------------------------

    fun testMoneyWidensToNumberOneWayOnly() {
        assertTrue(KrakenType.Number.isAssignableFrom(KrakenType.Money))
        assertFalse(KrakenType.Money.isAssignableFrom(KrakenType.Number))
    }

    /** Le piège classique de KEL : Date et DateTime ne se comparent pas. */
    fun testDateIsNotComparableWithDateTime() {
        assertFalse(KrakenType.Date.isComparableWith(KrakenType.DateTime))
        assertTrue(KrakenType.Date.isComparableWith(KrakenType.Date))
        assertTrue(KrakenType.Money.isComparableWith(KrakenType.Number))
    }

    fun testAnythingGoesWithAnyAndUnknown() {
        assertTrue(KrakenType.Date.isComparableWith(KrakenType.Any))
        assertTrue(KrakenType.Date.isComparableWith(KrakenType.Unknown))
        assertTrue(KrakenType.String.isAssignableFrom(KrakenType.Unknown))
    }
}
