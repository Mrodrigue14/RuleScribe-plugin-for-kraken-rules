package com.kraken.plugin

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Les formes de types que `Value.g4` définit.
 *
 * Sa production `type` couvre `identifier`, `( type )`, `type[]`,
 * `type | type` et `<identifier>`. Le plugin n'en connaissait que la première,
 * le suffixe tableau et une forme générique différente — si bien qu'un type
 * union ne parsait pas, alors que le catalogue embarqué porte déjà
 * `"Date | DateTime"` pour trois natives et que `KrakenType.fromDslName`
 * découpe déjà sur `|`. La couche types savait ce qu'est une union, la
 * grammaire non.
 *
 * Le corpus officiel ne pouvait pas révéler le trou : ses unions vivent dans
 * les fonctions natives annotées en Java, jamais dans du DSL source.
 */
class KrakenTypeSyntaxTest : BasePlatformTestCase() {

    private fun errors(source: String): List<String> {
        myFixture.configureByText("types.rules", source)
        return PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
            .map { it.errorDescription }
    }

    private fun assertParses(source: String) =
        assertEquals("devrait parser : $source", emptyList<String>(), errors(source))

    fun testUnionParameter() =
        assertParses("""Function GetDay(Date | DateTime d) : Number { 1 }""")

    fun testUnionReturnType() =
        assertParses("""Function Ret(Date d) : Number | String { 1 }""")

    /** Une signature nue, sans corps : la forme qui redéclare une native. */
    fun testUnionInABareSignature() =
        assertParses("""Function Sig(Date | DateTime) : Number""")

    fun testUnionOfMoreThanTwoMembers() =
        assertParses("""Function T(Date | DateTime | String d) : Number { 1 }""")

    /** `[]` lie plus fort que `|`, comme dans la grammaire officielle. */
    fun testUnionOfArrays() =
        assertParses("""Function Arr(Number[] | String[] xs) : Number { 1 }""")

    /** Les parenthèses permettent de forcer l'autre lecture. */
    fun testParenthesisedType() =
        assertParses("""Function P((Date | DateTime) d) : Number { 1 }""")

    fun testGenericTypeReference() =
        assertParses("""Function G(<T> x) : Number { 1 }""")

    /** Les bornes génériques précèdent le nom, comme dans KrakenDSL.g4. */
    fun testGenericBoundsStillParse() =
        assertParses("""Function <T is Number> B(T x) : T { x }""")

    /** Ce qui marchait avant doit continuer. */
    fun testPlainAndArrayTypesAreUnaffected() {
        assertParses("""Function A(Number n) : String { "x" }""")
        assertParses("""Function B(Coverage[] cs) : Number[] { cs.limit }""")
    }

    /** `|` reste utilisable comme opérateur dans une expression. */
    fun testBarStillWorksAsAnExpressionOperator() =
        assertParses(
            """
            Rule "R" On Policy.state {
                Assert a | b
            }
            """.trimIndent()
        )
}
