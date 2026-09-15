package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenFunctionGenericBoundInspection
import com.kraken.plugin.inspection.KrakenFunctionNativeDuplicateInspection
import com.kraken.plugin.inspection.KrakenFunctionParameterDuplicateInspection
import com.kraken.plugin.inspection.KrakenFunctionTypeUnionGenericMixInspection

/**
 * `Function` declaration checks, mirroring `FunctionValidator` and
 * `FunctionSignatureValidator`.
 *
 * The tests check the code as much as the trigger: the code links the editor underline
 * to the build log line, and it depends on whether the declaration has a body.
 */
class KrakenFunctionDeclInspectionTest : BasePlatformTestCase() {

    private fun codesFor(source: String): List<String> {
        myFixture.enableInspections(
            KrakenFunctionGenericBoundInspection(),
            KrakenFunctionTypeUnionGenericMixInspection(),
            KrakenFunctionParameterDuplicateInspection(),
            KrakenFunctionNativeDuplicateInspection(),
        )
        myFixture.configureByText("test.rules", source.trimIndent())
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .mapNotNull { CODE.find(it)?.groupValues?.get(1) }
            .sorted()
    }

    private fun assertCodes(expected: List<String>, source: String) = assertEquals(expected.sorted(), codesFor(source))

    fun testDuplicateGenericBoundIsReported() = assertCodes(
        listOf("kvf004"),
        """
        Function <T is Number, T is String> Dup(<T> p) : Number {
            1
        }
        """,
    )

    /** The same defect without a body: a signature, so a different code. */
    fun testDuplicateGenericBoundInSignatureUsesSignatureCode() = assertCodes(
        listOf("kvf017"),
        "Function <T is Number, T is String> Dup(<T> p) : Number",
    )

    fun testGenericBoundThatIsItselfGenericIsReported() = assertCodes(
        listOf("kvf005"),
        """
        Function <T is <G>> Itself(<T> p) : Number {
            1
        }
        """,
    )

    fun testGenericBoundThatIsItselfGenericInSignatureUsesSignatureCode() = assertCodes(
        listOf("kvf018"),
        "Function <T is <G>> Itself(<T> p) : Number",
    )

    /** A generic bound also hides behind an array suffix. */
    fun testArrayOfGenericIsStillAGenericBound() = assertCodes(
        listOf("kvf005"),
        """
        Function <T is <G>[]> Itself(<T> p) : Number {
            1
        }
        """,
    )

    /** The exact case of `FunctionValidatorTest.shouldFailWhenGenericsAreMixedWithUnion`. */
    fun testUnionGenericMixIsReportedOnBothPositions() = assertCodes(
        listOf("kvf007", "kvf010"),
        """
        Function <T is Number> First(<T>[] | String p) : <T> | String {
            1
        }
        """,
    )

    fun testUnionGenericMixInSignatureUsesSignatureCodes() = assertCodes(
        listOf("kvf020", "kvf021"),
        "Function <T is Number> First(<T>[] | String p) : <T> | String",
    )

    /** A union without generics is valid. */
    fun testPlainUnionIsNotReported() = assertCodes(
        emptyList(),
        """
        Function DayOfWeek(Date | DateTime d) : Number {
            1
        }
        """,
    )

    /**
     * `Foo<Bar>` is not a generic: the grammar tolerates it, and confusing it with `<T>`
     * would condemn valid code.
     */
    fun testParameterisedTypeIsNotAGeneric() = assertCodes(
        emptyList(),
        """
        Function Mix(Date | Foo<Bar> d) : Number {
            1
        }
        """,
    )

    /**
     * The union is in the bound, not in the parameter type. The engine resolves the bound
     * and might report this, but none of its tests pins it, so RuleScribe abstains.
     */
    fun testUnionInsideTheBoundIsNotReported() = assertCodes(
        emptyList(),
        """
        Function <T is Date | DateTime, N is Number> First(<T>[] dates, <N> index) : <T> {
            dates[0]
        }
        """,
    )

    fun testDuplicateParameterNameIsReported() = assertCodes(
        listOf("kvf008"),
        """
        Function Twice(Number a, Number a) : Number {
            a
        }
        """,
    )

    fun testDistinctParameterNamesAreNotReported() = assertCodes(
        emptyList(),
        """
        Function Total(Number a, Number b) : Number {
            a + b
        }
        """,
    )

    fun testFunctionShadowingANativeIsReported() = assertCodes(
        listOf("kvf003"),
        """
        Function Count(Number[] items) : Number {
            1
        }
        """,
    )

    /**
     * Without a body, the declaration is how a Java function is declared:
     * `FunctionSignatureValidator` does not run this check.
     */
    fun testSignatureNamedAfterANativeIsNotReported() = assertCodes(
        emptyList(),
        "Function Count(Number[] items) : Number",
    )

    fun testOrdinaryFunctionIsNotReported() = assertCodes(
        emptyList(),
        """
        Function Premium(Coverage[] coverages) : Number {
            coverages[0].limit
        }
        """,
    )

    private companion object {
        val CODE = Regex("""^\[(kvf\d+)]""")
    }
}
