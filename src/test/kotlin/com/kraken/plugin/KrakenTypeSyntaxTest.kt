package com.kraken.plugin

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The type forms `Value.g4` defines: `identifier`, `( type )`, `type[]`,
 * `type | type`, `<identifier>`.
 *
 * The official corpus cannot catch a gap here: its unions live in natives annotated in
 * Java, never in DSL source.
 */
class KrakenTypeSyntaxTest : BasePlatformTestCase() {

    private fun errors(source: String): List<String> {
        myFixture.configureByText("types.rules", source)
        return PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
            .map { it.errorDescription }
    }

    private fun assertParses(source: String) = assertEquals("should parse: $source", emptyList<String>(), errors(source))

    fun testUnionParameter() = assertParses("""Function GetDay(Date | DateTime d) : Number { 1 }""")

    fun testUnionReturnType() = assertParses("""Function Ret(Date d) : Number | String { 1 }""")

    /** A bare signature without a body, the form that redeclares a native. */
    fun testUnionInABareSignature() = assertParses("""Function Sig(Date | DateTime) : Number""")

    fun testUnionOfMoreThanTwoMembers() = assertParses("""Function T(Date | DateTime | String d) : Number { 1 }""")

    /** `[]` binds tighter than `|`, as in the official grammar. */
    fun testUnionOfArrays() = assertParses("""Function Arr(Number[] | String[] xs) : Number { 1 }""")

    /** Parentheses force the other reading. */
    fun testParenthesisedType() = assertParses("""Function P((Date | DateTime) d) : Number { 1 }""")

    fun testGenericTypeReference() = assertParses("""Function G(<T> x) : Number { 1 }""")

    /** Generic bounds precede the name, as in KrakenDSL.g4. */
    fun testGenericBoundsStillParse() = assertParses("""Function <T is Number> B(T x) : T { x }""")

    fun testPlainAndArrayTypesAreUnaffected() {
        assertParses("""Function A(Number n) : String { "x" }""")
        assertParses("""Function B(Coverage[] cs) : Number[] { cs.limit }""")
    }

    /** `|` still works as an expression operator. */
    fun testBarStillWorksAsAnExpressionOperator() = assertParses(
        """
            Rule "R" On Policy.state {
                Assert a | b
            }
        """.trimIndent(),
    )
}
