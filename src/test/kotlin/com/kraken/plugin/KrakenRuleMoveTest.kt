package com.kraken.plugin

import com.intellij.openapi.command.WriteCommandAction
import com.kraken.plugin.refactoring.KrakenDeclarationMover

/**
 * Rule move, end to end.
 *
 * Resolution is checked afterwards, not text: a Kraken reference names a rule, never a
 * file. Both files must also still parse, since the move goes through the document.
 */
class KrakenRuleMoveTest : KrakenMoveTestCase() {

    fun testRuleLandsInTheTargetAndLeavesTheSource() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Moved" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val b = file("b.rules", "Namespace Policy")

        WriteCommandAction.runWriteCommandAction(project) { KrakenDeclarationMover.move(project, ruleIn(a, "Moved"), b) }

        assertTrue("the rule must be in the destination", b.text.contains("""Rule "Moved""""))
        assertFalse("and have left the source", a.text.contains("""Rule "Moved""""))
        assertParses(a, b)
    }

    /** The reference is unchanged and still resolves. */
    fun testReferencesStillResolveWhenTheDestinationIsVisible() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Moved" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val b = file("b.rules", "Namespace Policy")
        val ep = file(
            "ep.rules",
            """
            Namespace Policy
            EntryPoint "Validation" { "Moved" }
            """.trimIndent(),
        )

        assertNotNull("resolves before", ruleRefIn(ep, "Moved").reference.resolve())
        WriteCommandAction.runWriteCommandAction(project) { KrakenDeclarationMover.move(project, ruleIn(a, "Moved"), b) }
        assertNotNull("must resolve after", ruleRefIn(ep, "Moved").reference.resolve())
        assertParses(a, b, ep)
    }

    /**
     * The damage conflict analysis announces: the reference text is intact, but it no
     * longer finds anything.
     */
    fun testReferencesStopResolvingWhenTheDestinationIsOutOfSight() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Moved" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val far = file("far.rules", "Namespace Unrelated")
        val ep = file(
            "ep.rules",
            """
            Namespace Policy
            EntryPoint "Validation" { "Moved" }
            """.trimIndent(),
        )

        assertNotNull("resolves before", ruleRefIn(ep, "Moved").reference.resolve())
        WriteCommandAction.runWriteCommandAction(project) { KrakenDeclarationMover.move(project, ruleIn(a, "Moved"), far) }
        assertEquals("the reference text is unchanged", "Moved", ruleRefIn(ep, "Moved").ruleName)
        assertNull("but it no longer resolves", ruleRefIn(ep, "Moved").reference.resolve())
    }

    /** After the move, the stub index finds the rule in its new file. */
    fun testTheStubIndexFindsTheRuleInItsNewHome() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Moved" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val b = file("b.rules", "Namespace Policy")

        WriteCommandAction.runWriteCommandAction(project) { KrakenDeclarationMover.move(project, ruleIn(a, "Moved"), b) }

        val found = com.kraken.plugin.psi.KrakenPsiUtil.findRulesVisible(b, "Moved")
        assertEquals("a single declaration, in the destination", 1, found.size)
        assertEquals("b.rules", found.first().containingFile.name)
    }

    /** Moving into the same file does nothing. */
    fun testMovingIntoTheSameFileIsARefusal() {
        val a = file(
            "a.rules",
            """
            Namespace Policy
            Rule "Stay" On Policy.state { Assert true }
            """.trimIndent(),
        )
        val before = a.text
        WriteCommandAction.runWriteCommandAction(project) { KrakenDeclarationMover.move(project, ruleIn(a, "Stay"), a) }
        assertEquals(before, a.text)
    }
}
