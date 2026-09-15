package com.kraken.plugin

import com.kraken.plugin.types.KrakenTypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Syntactic shape of a type, which the two union/generic mix inspections rely on.
 *
 * The grammar has two angle bracket constructs: `<T>` is the `Value.g4` generic, while
 * `Foo<A, B>` does not exist there but RuleScribe accepts it. Confusing them would treat
 * `Date | Foo<Bar>` as a union/generic mix and condemn valid code.
 */
class KrakenTypeTokenTest {

    private fun parse(raw: String): KrakenTypeToken = KrakenTypeToken.parse(raw) ?: throw AssertionError("unparseable type: $raw")

    @Test
    fun `a plain type is neither union nor generic`() {
        val type = parse("Number")
        assertFalse(type.isUnion)
        assertFalse(type.isGeneric)
    }

    @Test
    fun `a generic is generic`() {
        assertTrue(parse("<T>").isGeneric)
        assertFalse(parse("<T>").isUnion)
    }

    @Test
    fun `a parameterised type is not a generic`() {
        // `Foo<Bar>` denotes Foo, not a generic named Bar.
        assertFalse(parse("Foo<Bar>").isGeneric)
        assertFalse(parse("Foo<A, B>").isGeneric)
        assertEquals(KrakenTypeToken.Plain("Foo"), parse("Foo<Bar>"))
    }

    @Test
    fun `a union is a union`() {
        assertTrue(parse("Date | DateTime").isUnion)
        assertFalse(parse("Date | DateTime").isGeneric)
    }

    /** `ArrayType.isGeneric` and `isUnion` both delegate to the element. */
    @Test
    fun `an array takes the shape of its element`() {
        assertTrue(parse("<T>[]").isGeneric)
        assertTrue(parse("(Date | DateTime)[]").isUnion)
        assertFalse(parse("Number[]").isUnion)
    }

    @Test
    fun `a union and generic mix is detected in both orders`() {
        for (raw in listOf("<T> | String", "String | <T>", "<T>[] | String")) {
            val type = parse(raw)
            assertTrue("$raw should be a union", type.isUnion)
            assertTrue("$raw should be generic", type.isGeneric)
        }
    }

    /** The exact case from the engine's `FunctionValidatorTest`. */
    @Test
    fun `the engine case mixes both`() {
        assertTrue(parse("<T>[] | String").let { it.isUnion && it.isGeneric })
    }

    /** `[]` binds tighter than `|`, as in `Value.g4`. */
    @Test
    fun `the bracket binds tighter than the bar`() {
        assertEquals(
            KrakenTypeToken.Union(
                KrakenTypeToken.Array(KrakenTypeToken.Plain("Date")),
                KrakenTypeToken.Plain("String"),
            ),
            parse("Date[] | String"),
        )
    }

    @Test
    fun `an unparseable type claims nothing`() {
        assertNull(KrakenTypeToken.parse(""))
        assertNull(KrakenTypeToken.parse("Date |"))
        assertNull(KrakenTypeToken.parse("<T"))
        assertNull(KrakenTypeToken.parse("Number["))
        assertNull(KrakenTypeToken.parse("Date @ String"))
    }
}
