package com.kraken.plugin

import com.intellij.openapi.command.WriteCommandAction
import com.kraken.plugin.refactoring.KrakenDeclarationMover

/**
 * Le déplacement de règle bout en bout.
 *
 * Ce qui est vérifié est la **résolution** après coup, pas le texte : une
 * référence Kraken nomme une règle, jamais un fichier, donc comparer des
 * chaînes ne dirait rien de ce qui compte. On contrôle aussi que les deux
 * fichiers parsent encore, puisque le déplacement passe par le document.
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

        assertTrue("la règle doit être dans la destination", b.text.contains("""Rule "Moved""""))
        assertFalse("et avoir quitté la source", a.text.contains("""Rule "Moved""""))
        assertParses(a, b)
    }

    /** Le point qui compte : la référence n'a pas bougé et résout toujours. */
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

        assertNotNull("résout avant", ruleRefIn(ep, "Moved").reference.resolve())
        WriteCommandAction.runWriteCommandAction(project) { KrakenDeclarationMover.move(project, ruleIn(a, "Moved"), b) }
        assertNotNull("doit résoudre après", ruleRefIn(ep, "Moved").reference.resolve())
        assertParses(a, b, ep)
    }

    /**
     * Et l'inverse, qui est le dommage que l'analyse de conflit sert à
     * annoncer : le texte de la référence est intact, mais elle ne trouve
     * plus rien.
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

        assertNotNull("résout avant", ruleRefIn(ep, "Moved").reference.resolve())
        WriteCommandAction.runWriteCommandAction(project) { KrakenDeclarationMover.move(project, ruleIn(a, "Moved"), far) }
        assertEquals("le texte de la référence est inchangé", "Moved", ruleRefIn(ep, "Moved").ruleName)
        assertNull("mais elle ne résout plus", ruleRefIn(ep, "Moved").reference.resolve())
    }

    /** Après déplacement, l'index de stubs doit retrouver la règle chez elle. */
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
        assertEquals("une seule déclaration, dans la destination", 1, found.size)
        assertEquals("b.rules", found.first().containingFile.name)
    }

    /** Déplacer vers son propre fichier ne fait rien. */
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
