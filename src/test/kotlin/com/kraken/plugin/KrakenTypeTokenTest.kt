package com.kraken.plugin

import com.kraken.plugin.types.KrakenTypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Forme syntaxique d'un type, sur laquelle reposent les deux inspections du
 * mélange union/générique.
 *
 * Le piège que ces tests gardent : la grammaire a **deux** constructions en
 * chevrons. `<T>` est le générique de `Value.g4` ; `Foo<A, B>` n'y existe pas,
 * mais RuleScribe l'accepte depuis toujours. Les confondre ferait passer
 * `Date | Foo<Bar>` pour un mélange union/générique et condamnerait du code
 * valide.
 */
class KrakenTypeTokenTest {

    private fun parse(raw: String): KrakenTypeToken =
        KrakenTypeToken.parse(raw) ?: throw AssertionError("type non analysable : $raw")

    @Test
    fun `un type simple n'est ni union ni générique`() {
        val type = parse("Number")
        assertFalse(type.isUnion)
        assertFalse(type.isGeneric)
    }

    @Test
    fun `un générique est générique`() {
        assertTrue(parse("<T>").isGeneric)
        assertFalse(parse("<T>").isUnion)
    }

    @Test
    fun `un type paramétré n'est pas un générique`() {
        // `Foo<Bar>` désigne Foo, pas un générique nommé Bar.
        assertFalse(parse("Foo<Bar>").isGeneric)
        assertFalse(parse("Foo<A, B>").isGeneric)
        assertEquals(KrakenTypeToken.Plain("Foo"), parse("Foo<Bar>"))
    }

    @Test
    fun `une union est une union`() {
        assertTrue(parse("Date | DateTime").isUnion)
        assertFalse(parse("Date | DateTime").isGeneric)
    }

    /** `ArrayType.isGeneric` et `isUnion` délèguent tous deux à leur élément. */
    @Test
    fun `le tableau hérite de la forme de son élément`() {
        assertTrue(parse("<T>[]").isGeneric)
        assertTrue(parse("(Date | DateTime)[]").isUnion)
        assertFalse(parse("Number[]").isUnion)
    }

    @Test
    fun `le mélange union générique est détecté dans les deux sens`() {
        for (raw in listOf("<T> | String", "String | <T>", "<T>[] | String")) {
            val type = parse(raw)
            assertTrue("$raw devrait être une union", type.isUnion)
            assertTrue("$raw devrait être générique", type.isGeneric)
        }
    }

    /** Le cas exact des tests du moteur (`FunctionValidatorTest`). */
    @Test
    fun `le cas du moteur mélange bien les deux`() {
        assertTrue(parse("<T>[] | String").let { it.isUnion && it.isGeneric })
    }

    /** `[]` lie plus fort que `|`, comme dans `Value.g4`. */
    @Test
    fun `le crochet lie plus fort que la barre`() {
        assertEquals(
            KrakenTypeToken.Union(
                KrakenTypeToken.Array(KrakenTypeToken.Plain("Date")),
                KrakenTypeToken.Plain("String"),
            ),
            parse("Date[] | String")
        )
    }

    @Test
    fun `un type non analysable ne prétend rien`() {
        assertNull(KrakenTypeToken.parse(""))
        assertNull(KrakenTypeToken.parse("Date |"))
        assertNull(KrakenTypeToken.parse("<T"))
        assertNull(KrakenTypeToken.parse("Number["))
        assertNull(KrakenTypeToken.parse("Date @ String"))
    }
}
