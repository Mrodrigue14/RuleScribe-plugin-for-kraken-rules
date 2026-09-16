package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.highlighter.KrakenSyntaxHighlighter

/**
 * Brace matching: depth and unmatched braces.
 *
 * These tests check the emitted ranges, not what the user sees (palette readability is
 * checked in `runIde`). They pin the stack logic: which brace sits at which depth, and
 * which one has no partner.
 */
class KrakenBracketColouringTest : BasePlatformTestCase() {

    /** Annotated character → name of the key set on it. */
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

    private fun depthsOf(source: String): List<String> = painted(source).filter { it.second != UNMATCHED }.map { it.second }

    private fun unmatchedIn(source: String): List<String> = painted(source).filter { it.second == UNMATCHED }.map { it.first }

    fun testNestingCyclesThroughTheDepthColours() {
        val depths = depthsOf(
            """
            Rule "R" On Policy.state {
                Assert Round(Sum(a)) > 0
            }
            """.trimIndent(),
        ).distinct()
        // `{` at depth 0, the `(` of Round at depth 1, the `(` of Sum at depth 2.
        assertEquals(
            listOf(DEPTH[0], DEPTH[1], DEPTH[2]).sorted(),
            depths.sorted(),
        )
    }

    fun testDepthWrapsAroundThePalette() {
        val depths = depthsOf(
            """
            Rule "R" On Policy.state {
                Assert Round(Sum(Count(a))) > 0
            }
            """.trimIndent(),
        )
        assertTrue("the 4th level reuses the 1st colour", depths.count { it == DEPTH[0] } >= 4)
    }

    fun testBalancedBracketsAreNeverMarkedUnmatched() {
        assertEquals(
            emptyList<String>(),
            unmatchedIn(
                """
                Rule "R" On Policy.state {
                    Assert Count(Coverage[limit > 0]) = 1
                }
                """.trimIndent(),
            ),
        )
    }

    /** `?[` opens a bracket; otherwise the `]` would look unmatched. */
    fun testNullSafeBracketCountsAsAnOpener() {
        assertEquals(
            emptyList<String>(),
            unmatchedIn(
                """
                Rule "R" On Policy.state {
                    Assert Count(Coverage?[limit > 0]) = 1
                }
                """.trimIndent(),
            ),
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
                """.trimIndent(),
            ),
        )
    }

    /**
     * Ambiguous match: the closing brace does not match the opener on top of the stack.
     * Red must cover this as well as a plainly unmatched brace.
     */
    fun testAMismatchedPairIsMarked() {
        val unmatched = unmatchedIn(
            """
            Rule "R" On Policy.state {
                Assert Round(a] > 0
            }
            """.trimIndent(),
        )
        assertTrue("the mismatched closing bracket is marked: $unmatched", unmatched.contains("]"))
        assertTrue("so is the opener left alone: $unmatched", unmatched.contains("("))
    }

    private companion object {
        val DEPTH = KrakenSyntaxHighlighter.BRACKET_DEPTH.map { it.externalName }
        val UNMATCHED = KrakenSyntaxHighlighter.UNMATCHED_BRACKET.externalName
    }
}
