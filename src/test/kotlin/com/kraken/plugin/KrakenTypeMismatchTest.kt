package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenTypeMismatchInspection

/**
 * The type mismatch inspection.
 *
 * As for identifiers, most tests check that it stays silent: the plugin does not type
 * everything, and an invented diagnostic costs more than a missed one.
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
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenTypeMismatchInspection())
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("[kvr049]") }
    }

    fun testDateComparedWithDateTimeIsReported() {
        assertEquals(
            listOf(
                "[kvr049] Operation LessThan can only be performed on comparable types, " +
                    "but was performed on 'Date' and 'DateTime'.",
            ),
            problems("Assert effectiveDate < createdOn"),
        )
    }

    fun testStringComparedWithNumberIsReported() {
        assertEquals(
            listOf(
                "[kvr049] Operation LessThan can only be performed on comparable types, " +
                    "but was performed on 'String' and 'Number'.",
            ),
            problems("Assert policyCd < premium"),
        )
    }

    /**
     * Two `String`s cannot be ordered any more than a `Date` and a `DateTime`: the engine's
     * `isComparableWith` only knows numbers, dates and date-times.
     */
    fun testTwoStringsCannotBeOrdered() {
        assertEquals(
            listOf(
                "[kvr049] Operation LessThan can only be performed on comparable types, " +
                    "but was performed on 'String' and 'String'.",
            ),
            problems("Assert policyCd < policyCd"),
        )
    }

    /** Equality accepts two `String`s: assignability applies. */
    fun testTwoStringsCanBeCompared() {
        assertEquals(emptyList<String>(), problems("Assert policyCd = policyCd"))
    }

    fun testEqualityBetweenUnrelatedTypesIsReported() {
        assertEquals(
            listOf(
                "[kvr049] Both sides of operator 'Equals' must have same type, " +
                    "but left side was of type 'String' and right side was of type 'Number'.",
            ),
            problems("Assert policyCd = premium"),
        )
    }

    /** `Money` is assignable to `Number`, in that direction. */
    fun testEqualityBetweenMoneyAndNumberIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert limitAmount = premium"))
    }

    /** `>=` and `<=` must lex as single tokens for the right operand to be found. */
    fun testWideComparisonIsChecked() {
        assertEquals(
            listOf(
                "[kvr049] Operation MoreThanOrEquals can only be performed on comparable types, " +
                    "but was performed on 'Date' and 'DateTime'.",
            ),
            problems("Assert effectiveDate >= createdOn"),
        )
        assertEquals(
            listOf(
                "[kvr049] Operation LessThanOrEquals can only be performed on comparable types, " +
                    "but was performed on 'Date' and 'DateTime'.",
            ),
            problems("Assert effectiveDate <= createdOn"),
        )
    }

    fun testWideComparisonBetweenComparableTypesIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert limitAmount >= premium"))
    }

    fun testMoneyAndNumberAreComparable() {
        assertEquals(emptyList<String>(), problems("Assert limitAmount < premium"))
    }

    fun testSameTypesAreComparable() {
        assertEquals(emptyList<String>(), problems("Assert effectiveDate < effectiveDate"))
    }

    fun testDateLiteralComparesWithADateField() {
        assertEquals(emptyList<String>(), problems("Assert effectiveDate < 2020-01-01"))
    }

    fun testUnknownOperandIsNotJudged() {
        assertEquals(emptyList<String>(), problems("Assert effectiveDate < whatIsThis"))
    }

    fun testWrongArgumentTypeIsReported() {
        val reported = problems("Assert Round(policyCd) > 0")
        assertEquals(1, reported.size)
        assertEquals(
            "[kvr049] Incompatible type 'String' of function parameter at index 0 " +
                "when invoking function Round. Expected type is 'Number'.",
            reported.single(),
        )
    }

    fun testMoneyIsAcceptedWhereNumberIsExpected() {
        assertEquals("Money narrows to Number", emptyList<String>(), problems("Assert Round(limitAmount) > 0"))
    }

    /** A union parameter (`Date | DateTime`) is dynamic: anything passes. */
    fun testUnionParameterAcceptsAnything() {
        assertEquals(emptyList<String>(), problems("Assert GetDay(effectiveDate) > 0"))
        assertEquals(emptyList<String>(), problems("Assert GetDay(createdOn) > 0"))
    }

    fun testArgumentOfUnknownTypeIsNotJudged() {
        assertEquals(emptyList<String>(), problems("Assert Round(whatIsThis) > 0"))
    }

    /**
     * A call's type is its return type, not that of its arguments: a recursive segment
     * search would type `NumberOfDaysBetween(a, b)` as Date.
     */
    fun testCallTypeComesFromItsReturnNotItsArguments() {
        assertEquals(
            "NumberOfDaysBetween returns a Number",
            emptyList<String>(),
            problems("Assert NumberOfDaysBetween(effectiveDate, effectiveDate) < 365"),
        )
        assertEquals(
            "NumberToString returns a String",
            emptyList<String>(),
            problems("Assert NumberToString(premium) == \"11\""),
        )
    }

    /** A projection over a collection stays a collection. */
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
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenTypeMismatchInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("[kvr049]") }
        assertEquals(emptyList<String>(), reported)
    }

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
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenTypeMismatchInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("[kvr049]") }
        assertEquals("STRING, string and String are the same type", emptyList<String>(), reported)
    }

    /** Project functions are not checked: too noisy. */
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
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenTypeMismatchInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("[kvr049]") }
        assertEquals(emptyList<String>(), reported)
    }
}
