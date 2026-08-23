package com.kraken.plugin

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenRuleRef
import com.kraken.plugin.refactoring.KrakenDeclarationMover

/**
 * Le déplacement d'EntryPoint bout en bout.
 *
 * Comme pour une règle, ce qui est vérifié est la **résolution** après coup et
 * jamais le texte. La différence tient au sens : un EntryPoint référence aussi,
 * donc le déplacement peut le vider sans qu'une ligne bouge à l'intérieur, et
 * c'est ce cas-là qui a droit à son test.
 */
class KrakenEntryPointMoveTest : BasePlatformTestCase() {

    private fun file(name: String, text: String): KrakenFile =
        myFixture.addFileToProject(name, text) as KrakenFile

    private fun epIn(file: KrakenFile, name: String): KrakenEntryPointDecl =
        PsiTreeUtil.findChildrenOfType(file, KrakenEntryPointDecl::class.java).first { it.name == name }

    private fun ruleRefIn(file: KrakenFile, name: String): KrakenRuleRef =
        PsiTreeUtil.findChildrenOfType(file, KrakenRuleRef::class.java).first { it.ruleName == name }

    private fun epRefIn(file: KrakenFile, name: String): KrakenEpRef =
        PsiTreeUtil.findChildrenOfType(file, KrakenEpRef::class.java).first { it.entryPointName == name }

    private fun assertParses(vararg files: KrakenFile) {
        for (f in files) {
            val errors = PsiTreeUtil.findChildrenOfType(f, PsiErrorElement::class.java)
            assertEquals(
                "${f.name} ne parse plus :\n${f.text}", emptyList<String>(),
                errors.map { it.errorDescription }
            )
        }
    }

    fun testEntryPointLandsInTheTargetAndLeavesTheSource() {
        val a = file("a.rules", """
            Namespace Policy
            EntryPoint "Moved" { }
        """.trimIndent())
        val b = file("b.rules", "Namespace Policy")

        WriteCommandAction.runWriteCommandAction(project) {
            KrakenDeclarationMover.move(project, epIn(a, "Moved"), b)
        }

        assertTrue("l'entry point doit être dans la destination", b.text.contains("""EntryPoint "Moved""""))
        assertFalse("et avoir quitté la source", a.text.contains("""EntryPoint "Moved""""))
        assertParses(a, b)
    }

    /** Destination visible : le texte des items ne bouge pas et ils résolvent encore. */
    fun testItemsStillResolveWhenTheDestinationSeesThem() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
        """.trimIndent())
        val b = file("b.rules", "Namespace Policy")

        WriteCommandAction.runWriteCommandAction(project) {
            KrakenDeclarationMover.move(project, epIn(a, "Moving"), b)
        }

        assertNotNull("l'item doit résoudre depuis la destination", ruleRefIn(b, "Stays").reference.resolve())
        assertParses(a, b)
    }

    /**
     * Le cas propre à l'EntryPoint : son item garde exactement le même texte et
     * cesse de résoudre. C'est ce que l'analyse annonce avant d'écrire.
     */
    fun testOwnItemKeepsItsTextAndStopsResolving() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
        """.trimIndent())
        val far = file("far.rules", "Namespace Other")

        assertNotNull("l'item résout avant le déplacement", ruleRefIn(a, "Stays").reference.resolve())

        WriteCommandAction.runWriteCommandAction(project) {
            KrakenDeclarationMover.move(project, epIn(a, "Moving"), far)
        }

        val moved = ruleRefIn(far, "Stays")
        assertEquals("le texte de l'item est intact", "Stays", moved.ruleName)
        assertNull("mais il ne résout plus", moved.reference.resolve())
        assertParses(a, far)
    }

    /** L'autre sens : la référence entrante garde son texte et perd sa cible. */
    fun testIncomingReferenceKeepsItsTextAndStopsResolving() {
        val a = file("a.rules", """
            Namespace Policy
            EntryPoint "Moving" { }
        """.trimIndent())
        val far = file("far.rules", "Namespace Other")
        val outer = file("outer.rules", """
            Namespace Policy
            EntryPoint "Outer" { EntryPoint "Moving" }
        """.trimIndent())

        assertNotNull("la référence résout avant", epRefIn(outer, "Moving").reference?.resolve())

        WriteCommandAction.runWriteCommandAction(project) {
            KrakenDeclarationMover.move(project, epIn(a, "Moving"), far)
        }

        val ref = epRefIn(outer, "Moving")
        assertEquals("le texte de la référence est intact", "Moving", ref.entryPointName)
        assertNull("mais elle ne résout plus", ref.reference?.resolve())
        assertParses(a, far, outer)
    }
}
