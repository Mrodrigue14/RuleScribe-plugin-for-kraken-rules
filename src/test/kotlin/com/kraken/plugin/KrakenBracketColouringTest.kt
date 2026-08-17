package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.highlighter.KrakenSyntaxHighlighter

/**
 * Appariement des accolades : profondeur et orphelines.
 *
 * Ces tests vérifient les plages émises, pas ce que l'utilisateur voit — la
 * lisibilité de la palette se contrôle dans `runIde`, pas ici. Ce qu'ils
 * verrouillent, c'est la logique de pile : quelle accolade est à quelle
 * profondeur, et laquelle n'a pas de partenaire.
 */
class KrakenBracketColouringTest : BasePlatformTestCase() {

    /** Caractère annoté → nom de la clé posée dessus. */
    private fun painted(source: String): List<Pair<String, String>> {
        myFixture.configureByText("brackets.rules", source)
        val text = myFixture.file.text
        return myFixture.doHighlighting()
            .mapNotNull { info ->
                val key = info.forcedTextAttributesKey?.externalName ?: return@mapNotNull null
                if (!key.startsWith("KRAKEN_BRACKET_DEPTH") && key != UNMATCHED) return@mapNotNull null
                text.substring(info.startOffset, info.endOffset) to key
            }
    }

    private fun depthsOf(source: String): List<String> =
        painted(source).filter { it.second != UNMATCHED }.map { it.second }

    private fun unmatchedIn(source: String): List<String> =
        painted(source).filter { it.second == UNMATCHED }.map { it.first }

    fun testNestingCyclesThroughTheDepthColours() {
        val depths = depthsOf(
            """
            Rule "R" On Policy.state {
                Assert Round(Sum(a)) > 0
            }
            """.trimIndent()
        ).distinct()
        // `{` niveau 0, `(` de Round niveau 1, `(` de Sum niveau 2.
        assertEquals(
            listOf(DEPTH[0], DEPTH[1], DEPTH[2]).sorted(),
            depths.sorted()
        )
    }

    /** Au-delà de la palette, les teintes se répètent plutôt que de manquer. */
    fun testDepthWrapsAroundThePalette() {
        val depths = depthsOf(
            """
            Rule "R" On Policy.state {
                Assert Round(Sum(Count(a))) > 0
            }
            """.trimIndent()
        )
        assertTrue("le 4e niveau reprend la 1re teinte", depths.count { it == DEPTH[0] } >= 4)
    }

    fun testBalancedBracketsAreNeverMarkedUnmatched() {
        assertEquals(
            emptyList<String>(),
            unmatchedIn(
                """
                Rule "R" On Policy.state {
                    Assert Count(Coverage[limit > 0]) = 1
                }
                """.trimIndent()
            )
        )
    }

    /** `?[` ouvre un crochet : sans ça, le `]` passerait pour orphelin. */
    fun testNullSafeBracketCountsAsAnOpener() {
        assertEquals(
            emptyList<String>(),
            unmatchedIn(
                """
                Rule "R" On Policy.state {
                    Assert Count(Coverage?[limit > 0]) = 1
                }
                """.trimIndent()
            )
        )
    }

    fun testAnUnclosedBraceIsMarked() {
        assertEquals(listOf("{"), unmatchedIn("""Rule "R" On Policy.state { Assert true"""))
    }

    fun testAStrayClosingParenIsMarked() {
        assertEquals(
            listOf(")"),
            unmatchedIn(
                """
                Rule "R" On Policy.state {
                    Assert a) > 0
                }
                """.trimIndent()
            )
        )
    }

    /**
     * Appariement ambigu : la fermante ne correspond pas à l'ouvrante du
     * dessus de pile. C'est le cas que le rouge doit couvrir au même titre
     * qu'une orpheline franche.
     */
    fun testAMismatchedPairIsMarked() {
        val unmatched = unmatchedIn(
            """
            Rule "R" On Policy.state {
                Assert Round(a] > 0
            }
            """.trimIndent()
        )
        assertTrue("la fermante dépareillée est signalée : $unmatched", unmatched.contains("]"))
        assertTrue("l'ouvrante restée seule aussi : $unmatched", unmatched.contains("("))
    }

    private companion object {
        val DEPTH = KrakenSyntaxHighlighter.BRACKET_DEPTH.map { it.externalName }
        val UNMATCHED = KrakenSyntaxHighlighter.UNMATCHED_BRACKET.externalName
    }
}
