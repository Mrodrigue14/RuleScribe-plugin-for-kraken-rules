package com.kraken.plugin

import com.kraken.plugin.refactoring.KrakenMoveConflicts

/**
 * The analysis that decides whether moving a rule breaks something.
 *
 * Assertions are about resolution, never text: a Kraken reference names a rule, not a
 * file, so a move changes no reference text, only whether references still find their
 * target.
 */
class KrakenMoveConflictsTest : KrakenMoveTestCase() {

    /** Same namespace: nothing can break. */
    fun testMovingWithinTheSameNamespaceIsSafe() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val b = file("b.rules", "Namespace Policy")
        file(
            "ep.rules",
            """
            Namespace Policy
            EntryPoint "Validation" { "Shared" }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), b))
    }

    /** The case behind the analysis: the destination leaves the referrer's scope. */
    fun testMovingOutOfSightIsReported() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Unrelated")
        file(
            "ep.rules",
            """
            Namespace Policy
            EntryPoint "Validation" { "Shared" }
            """.trimIndent(),
        )

        val broken = KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), far)
        assertEquals("the EntryPoint reference must be reported", 1, broken.size)
        assertEquals("Shared", broken.first().ruleName)
    }

    /** An `Include` of the destination is enough to keep resolution. */
    fun testIncludingTheDestinationKeepsItResolving() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val other = file("other.rules", "Namespace Base")
        file(
            "ep.rules",
            """
            Namespace Policy
            Include Base
            EntryPoint "Validation" { "Shared" }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), other))
    }

    /**
     * `Import Rule` is a separate axis from `Include`: an import that already names the
     * destination namespace stays valid after the move.
     */
    fun testAnImportNamingTheDestinationKeepsItResolving() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val other = file("other.rules", "Namespace Base")
        file(
            "ep.rules",
            """
            Namespace Consumer
            Import Rule "Shared" From Base
            EntryPoint "Validation" { "Shared" }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), other))
    }

    /**
     * The trap: an import naming the old namespace points, after the move, to a namespace
     * that no longer contains the rule.
     */
    fun testAnImportNamingTheOldNamespaceBreaks() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val other = file("other.rules", "Namespace Base")
        file(
            "ep.rules",
            """
            Namespace Consumer
            Import Rule "Shared" From Policy
            EntryPoint "Validation" { "Shared" }
            """.trimIndent(),
        )

        val broken = KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), other)
        assertEquals("l'import pointe vers l'ancien namespace : $broken", 1, broken.size)
    }

    /** A rule that nobody references moves safely. */
    fun testAnUnreferencedRuleHasNoConflicts() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Lonely" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Unrelated")

        assertEquals(emptyList<Any>(), KrakenMoveConflicts.brokenBy(ruleIn(a, "Lonely"), far))
    }
}
