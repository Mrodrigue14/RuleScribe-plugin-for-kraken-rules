package com.kraken.plugin

import com.intellij.openapi.command.WriteCommandAction
import com.kraken.plugin.refactoring.KrakenDeclarationMover

/**
 * EntryPoint move, end to end.
 *
 * As for rules, resolution is checked afterwards, never text. An EntryPoint also
 * references rules, so the move can empty it without a line changing inside.
 */
class KrakenEntryPointMoveTest : KrakenMoveTestCase() {

    fun testEntryPointLandsInTheTargetAndLeavesTheSource() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            EntryPoint "Moved" { }
            """.trimIndent(),
        )
        val b = file("b.rules", "Namespace Policy")

        WriteCommandAction.runWriteCommandAction(project) {
            KrakenDeclarationMover.move(project, epIn(a, "Moved"), b)
        }

        assertTrue("the entry point must be in the destination", b.text.contains("""EntryPoint "Moved""""))
        assertFalse("and have left the source", a.text.contains("""EntryPoint "Moved""""))
        assertParses(a, b)
    }

    /** Visible destination: item text is unchanged and the items still resolve. */
    fun testItemsStillResolveWhenTheDestinationSeesThem() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
            """.trimIndent(),
        )
        val b = file("b.rules", "Namespace Policy")

        WriteCommandAction.runWriteCommandAction(project) {
            KrakenDeclarationMover.move(project, epIn(a, "Moving"), b)
        }

        assertNotNull("the item must resolve from the destination", ruleRefIn(b, "Stays").reference.resolve())
        assertParses(a, b)
    }

    /**
     * Specific to EntryPoints: the item keeps its exact text and stops resolving, which the
     * analysis announces before writing.
     */
    fun testOwnItemKeepsItsTextAndStopsResolving() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Other")

        assertNotNull("the item resolves before the move", ruleRefIn(a, "Stays").reference.resolve())

        WriteCommandAction.runWriteCommandAction(project) {
            KrakenDeclarationMover.move(project, epIn(a, "Moving"), far)
        }

        val moved = ruleRefIn(far, "Stays")
        assertEquals("the item text is intact", "Stays", moved.ruleName)
        assertNull("but it no longer resolves", moved.reference.resolve())
        assertParses(a, far)
    }

    /** The other direction: the incoming reference keeps its text and loses its target. */
    fun testIncomingReferenceKeepsItsTextAndStopsResolving() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            EntryPoint "Moving" { }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Other")
        val outer = file(
            "outer.rules",
            """
            Namespace Policy
            EntryPoint "Outer" { EntryPoint "Moving" }
            """.trimIndent(),
        )

        assertNotNull("the reference resolves before", epRefIn(outer, "Moving").reference?.resolve())

        WriteCommandAction.runWriteCommandAction(project) {
            KrakenDeclarationMover.move(project, epIn(a, "Moving"), far)
        }

        val ref = epRefIn(outer, "Moving")
        assertEquals("the reference text is intact", "Moving", ref.entryPointName)
        assertNull("but it no longer resolves", ref.reference?.resolve())
        assertParses(a, far, outer)
    }
}
