package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenTypeMismatchInspection

/**
 * L'inspection de types.
 *
 * Comme pour les identifiants, la majorité des tests vérifient qu'elle **se
 * tait** : le plugin ne type pas tout, et un diagnostic inventé coûte plus cher
 * qu'un diagnostic manqué.
 */
class KrakenTypeMismatchTest : BasePlatformTestCase() {

    private val model = """
        Root Context Policy {
            String policyCd
            Money limitAmount
            Decimal premium
            Date effectiveDate
            DateTime createdOn
            Boolean active
        }
    """.trimIndent()

    private fun problems(body: String): List<String> {
        myFixture.configureByText(
            "types.rules",
            """
            $model

            Rule "Under test" On Policy.policyCd {
                $body
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenTypeMismatchInspection())
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Cannot compare") || it.startsWith("Incompatible type") }
    }

    // ------------------------------------------------------------------
    // Comparaisons
    // ------------------------------------------------------------------

    /** Le piège classique : le moteur refuse Date contre DateTime. */
    fun testDateComparedWithDateTimeIsReported() {
        assertEquals(
            listOf("Cannot compare 'Date' with 'DateTime'"),
            problems("Assert effectiveDate < createdOn")
        )
    }

    fun testStringComparedWithNumberIsReported() {
        assertEquals(
            listOf("Cannot compare 'String' with 'Number'"),
            problems("Assert policyCd < premium")
        )
    }

    fun testMoneyAndNumberAreComparable() {
        assertEquals(emptyList<String>(), problems("Assert limitAmount < premium"))
    }

    fun testSameTypesAreComparable() {
        assertEquals(emptyList<String>(), problems("Assert effectiveDate < effectiveDate"))
    }

    /** Un littéral de date se compare bien à un champ Date. */
    fun testDateLiteralComparesWithADateField() {
        assertEquals(emptyList<String>(), problems("Assert effectiveDate < 2020-01-01"))
    }

    /** Un opérande non typé fait renoncer la vérification. */
    fun testUnknownOperandIsNotJudged() {
        assertEquals(emptyList<String>(), problems("Assert effectiveDate < whatIsThis"))
    }

    // ------------------------------------------------------------------
    // Arguments de fonction
    // ------------------------------------------------------------------

    fun testWrongArgumentTypeIsReported() {
        val reported = problems("Assert Round(policyCd) > 0")
        assertEquals(1, reported.size)
        assertTrue(reported.single().contains("Incompatible type 'String'"))
        assertTrue(reported.single().contains("expected 'Number'"))
    }

    fun testMoneyIsAcceptedWhereNumberIsExpected() {
        assertEquals("Money se rétrécit vers Number", emptyList<String>(), problems("Assert Round(limitAmount) > 0"))
    }

    /** Un paramètre d'union (`Date | DateTime`) est dynamique : tout passe. */
    fun testUnionParameterAcceptsAnything() {
        assertEquals(emptyList<String>(), problems("Assert GetDay(effectiveDate) > 0"))
        assertEquals(emptyList<String>(), problems("Assert GetDay(createdOn) > 0"))
    }

    fun testArgumentOfUnknownTypeIsNotJudged() {
        assertEquals(emptyList<String>(), problems("Assert Round(whatIsThis) > 0"))
    }

    /**
     * Le type d'un appel est son type de retour, pas celui de ce qu'il y a
     * dans ses arguments. Chercher les segments d'accès récursivement les
     * ramenait depuis l'intérieur des parenthèses : `NumberOfDaysBetween(a, b)`
     * était typé Date. Ce seul défaut produisait les 10 derniers faux positifs
     * du corpus officiel.
     */
    fun testCallTypeComesFromItsReturnNotItsArguments() {
        assertEquals(
            "NumberOfDaysBetween renvoie un Number",
            emptyList<String>(),
            problems("Assert NumberOfDaysBetween(effectiveDate, effectiveDate) < 365")
        )
        assertEquals(
            "NumberToString renvoie un String",
            emptyList<String>(),
            problems("Assert NumberToString(premium) == \"11\"")
        )
    }

    /** Une projection sur une collection reste une collection. */
    fun testProjectionOverACollectionIsNotJudged() {
        myFixture.configureByText(
            "projection.rules",
            """
            Root Context Policy {
                String policyCd
                Child* Coverage
            }

            Context Coverage {
                Money limit
            }

            Rule "Projects" On Policy.policyCd {
                Assert Sum(Coverage.limit) > 0
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenTypeMismatchInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Incompatible type") || it.startsWith("Cannot compare") }
        assertEquals(emptyList<String>(), reported)
    }

    /** Les noms de types du DSL sont insensibles à la casse. */
    fun testTypeNamesAreCaseInsensitive() {
        myFixture.configureByText(
            "casing.rules",
            """
            Root Context Policy {
                STRING code
                string label
                Datetime createdOn
            }

            Rule "Mixed casing" On Policy.code {
                Assert code = label
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenTypeMismatchInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Cannot compare") }
        assertEquals("STRING, string et String sont le même type", emptyList<String>(), reported)
    }

    /** Les fonctions du projet ne sont pas vérifiées : trop de bruit. */
    fun testDeclaredFunctionArgumentsAreNotChecked() {
        myFixture.configureByText(
            "declared.rules",
            """
            $model

            Function Twice(Number n) : Number {
                n * 2
            }

            Rule "Uses it" On Policy.policyCd {
                Assert Twice(policyCd) > 0
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenTypeMismatchInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Incompatible type") }
        assertEquals(emptyList<String>(), reported)
    }
}
