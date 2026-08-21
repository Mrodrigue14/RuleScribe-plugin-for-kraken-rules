package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenFunctionGenericBoundInspection
import com.kraken.plugin.inspection.KrakenFunctionNativeDuplicateInspection
import com.kraken.plugin.inspection.KrakenFunctionParameterDuplicateInspection
import com.kraken.plugin.inspection.KrakenFunctionTypeUnionGenericMixInspection

/**
 * Validation des déclarations `Function`, miroir de `FunctionValidator` et
 * `FunctionSignatureValidator`.
 *
 * Les tests vérifient le **code** autant que le déclenchement : c'est lui qui
 * relie le soulignement dans l'éditeur à la ligne du log de build, et il change
 * selon que la déclaration a un corps ou non.
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

    private fun assertCodes(expected: List<String>, source: String) =
        assertEquals(expected.sorted(), codesFor(source))

    // ------------------------------------------------------------------
    // Bornes génériques
    // ------------------------------------------------------------------

    fun testDuplicateGenericBoundIsReported() = assertCodes(
        listOf("kvf004"),
        """
        Function <T is Number, T is String> Dup(<T> p) : Number {
            1
        }
        """
    )

    /** Même défaut sans corps : c'est une signature, donc un autre code. */
    fun testDuplicateGenericBoundInSignatureUsesSignatureCode() = assertCodes(
        listOf("kvf017"),
        "Function <T is Number, T is String> Dup(<T> p) : Number"
    )

    fun testGenericBoundThatIsItselfGenericIsReported() = assertCodes(
        listOf("kvf005"),
        """
        Function <T is <G>> Itself(<T> p) : Number {
            1
        }
        """
    )

    fun testGenericBoundThatIsItselfGenericInSignatureUsesSignatureCode() = assertCodes(
        listOf("kvf018"),
        "Function <T is <G>> Itself(<T> p) : Number"
    )

    /** Une borne générique se cache aussi derrière un suffixe de tableau. */
    fun testArrayOfGenericIsStillAGenericBound() = assertCodes(
        listOf("kvf005"),
        """
        Function <T is <G>[]> Itself(<T> p) : Number {
            1
        }
        """
    )

    // ------------------------------------------------------------------
    // Mélange union / générique
    // ------------------------------------------------------------------

    /** Le cas exact de `FunctionValidatorTest.shouldFailWhenGenericsAreMixedWithUnion`. */
    fun testUnionGenericMixIsReportedOnBothPositions() = assertCodes(
        listOf("kvf007", "kvf010"),
        """
        Function <T is Number> First(<T>[] | String p) : <T> | String {
            1
        }
        """
    )

    fun testUnionGenericMixInSignatureUsesSignatureCodes() = assertCodes(
        listOf("kvf020", "kvf021"),
        "Function <T is Number> First(<T>[] | String p) : <T> | String"
    )

    /** Une union sans générique est parfaitement valide. */
    fun testPlainUnionIsNotReported() = assertCodes(
        emptyList(),
        """
        Function DayOfWeek(Date | DateTime d) : Number {
            1
        }
        """
    )

    /**
     * `Foo<Bar>` n'est pas un générique : la grammaire l'accepte par tolérance,
     * mais le confondre avec `<T>` condamnerait du code valide.
     */
    fun testParameterisedTypeIsNotAGeneric() = assertCodes(
        emptyList(),
        """
        Function Mix(Date | Foo<Bar> d) : Number {
            1
        }
        """
    )

    /**
     * L'union vit dans la **borne**, pas dans le type du paramètre. Le moteur
     * résout la borne et signalerait peut-être ce cas ; aucun de ses tests ne le
     * fixe, donc RuleScribe s'abstient plutôt que de condamner du code valide.
     */
    fun testUnionInsideTheBoundIsNotReported() = assertCodes(
        emptyList(),
        """
        Function <T is Date | DateTime, N is Number> First(<T>[] dates, <N> index) : <T> {
            dates[0]
        }
        """
    )

    // ------------------------------------------------------------------
    // Paramètres et natives
    // ------------------------------------------------------------------

    fun testDuplicateParameterNameIsReported() = assertCodes(
        listOf("kvf008"),
        """
        Function Twice(Number a, Number a) : Number {
            a
        }
        """
    )

    fun testDistinctParameterNamesAreNotReported() = assertCodes(
        emptyList(),
        """
        Function Total(Number a, Number b) : Number {
            a + b
        }
        """
    )

    fun testFunctionShadowingANativeIsReported() = assertCodes(
        listOf("kvf003"),
        """
        Function Count(Number[] items) : Number {
            1
        }
        """
    )

    /**
     * Sans corps, la déclaration *est* la façon de déclarer une fonction Java :
     * `FunctionSignatureValidator` ne fait pas cette vérification.
     */
    fun testSignatureNamedAfterANativeIsNotReported() = assertCodes(
        emptyList(),
        "Function Count(Number[] items) : Number"
    )

    fun testOrdinaryFunctionIsNotReported() = assertCodes(
        emptyList(),
        """
        Function Premium(Coverage[] coverages) : Number {
            coverages[0].limit
        }
        """
    )

    private companion object {
        val CODE = Regex("""^\[(kvf\d+)]""")
    }
}
