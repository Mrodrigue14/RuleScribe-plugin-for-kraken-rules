package com.kraken.plugin

import com.kraken.plugin.refactoring.KrakenMoveConflicts

/**
 * The analysis that decides whether moving an EntryPoint breaks something.
 *
 * A rule has one direction (who references it); an EntryPoint has two, because it
 * references others. The second is what a naive port of Move Rule would miss: an entry
 * point placed where its own rules are invisible becomes empty.
 */
class KrakenEntryPointMoveConflictsTest : KrakenMoveTestCase() {

    fun testMovingWithinTheSameNamespaceIsSafe() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
            EntryPoint "Inner" { "Shared" }
            """.trimIndent(),
        )
        val b = file("b.rules", "Namespace Policy")
        file(
            "outer.rules",
            """
            Namespace Policy
            EntryPoint "Outer" { EntryPoint "Inner" }
            """.trimIndent(),
        )

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Inner"), b)
        assertTrue("expected no conflict, got $broken", broken.isEmpty)
    }

    fun testIncomingReferenceThatLosesSightIsReported() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            EntryPoint "Inner" { }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Other")
        file(
            "outer.rules",
            """
            Namespace Policy
            EntryPoint "Outer" { EntryPoint "Inner" }
            """.trimIndent(),
        )

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Inner"), far)
        assertEquals(1, broken.incoming.size)
        assertEquals(emptyList<Any>(), broken.outgoing)
    }

    fun testIncludeKeepsTheIncomingReferenceAlive() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            EntryPoint "Inner" { }
            """.trimIndent(),
        )
        val shared = file("shared.rules", "Namespace Shared")
        file(
            "outer.rules",
            """
            Namespace Policy
            Include Shared
            EntryPoint "Outer" { EntryPoint "Inner" }
            """.trimIndent(),
        )

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Inner"), shared)
        assertTrue("Include should be enough, got $broken", broken.isEmpty)
    }

    /** The case a rule does not have: the entry point leaves without its own rules. */
    fun testOwnRuleItemThatStopsResolvingIsReported() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Other")

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertEquals(emptyList<Any>(), broken.incoming)
        assertEquals(1, broken.outgoing.size)
    }

    fun testImportAtTheDestinationRescuesTheOwnItem() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
            """.trimIndent(),
        )
        val far = file(
            "far.rules",
            """
            Namespace Other
            Import Rule "Stays" From Policy
            """.trimIndent(),
        )

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertTrue("the destination import should rescue the item, got $broken", broken.isEmpty)
    }

    /** A nested item only has the visibility axis, so no import rescues it. */
    fun testOwnNestedEntryPointItemThatStopsResolvingIsReported() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            EntryPoint "Leaf" { }
            EntryPoint "Moving" { EntryPoint "Leaf" }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Other")

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertEquals(1, broken.outgoing.size)
    }

    fun testItemThatAlreadyFailsToResolveIsNotCounted() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            EntryPoint "Moving" { "Nowhere" }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Other")

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertEquals(emptyList<Any>(), broken.outgoing)
    }

    fun testBothDirectionsAreCountedApart() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Other")
        file(
            "outer.rules",
            """
            Namespace Policy
            EntryPoint "Outer" { EntryPoint "Moving" }
            """.trimIndent(),
        )

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertEquals(1, broken.incoming.size)
        assertEquals(1, broken.outgoing.size)
        assertEquals(2, broken.incoming.size + broken.outgoing.size)
    }
}
