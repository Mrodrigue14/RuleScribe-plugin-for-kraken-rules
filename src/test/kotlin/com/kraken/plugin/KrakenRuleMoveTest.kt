package com.kraken.plugin

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.psi.KrakenRuleRef
import com.kraken.plugin.refactoring.KrakenRuleMover

/**
 * Le déplacement de règle bout en bout.
 *
 * Ce qui est vérifié est la **résolution** après coup, pas le texte : une
 * référence Kraken nomme une règle, jamais un fichier, donc comparer des
 * chaînes ne dirait rien de ce qui compte. On contrôle aussi que les deux
 * fichiers parsent encore, puisque le déplacement passe par le document.
 */
class KrakenRuleMoveTest : BasePlatformTestCase() {

    private fun file(name: String, text: String): KrakenFile =
        myFixture.addFileToProject(name, text) as KrakenFile

    private fun ruleIn(file: KrakenFile, name: String): KrakenRuleDecl =
        PsiTreeUtil.findChildrenOfType(file, KrakenRuleDecl::class.java).first { it.name == name }

    private fun refIn(file: KrakenFile, name: String): KrakenRuleRef =
        PsiTreeUtil.findChildrenOfType(file, KrakenRuleRef::class.java).first { it.ruleName == name }

    private fun assertParses(vararg files: KrakenFile) {
        for (f in files) {
            val errors = PsiTreeUtil.findChildrenOfType(f, PsiErrorElement::class.java)
            assertEquals("${f.name} ne parse plus :\n${f.text}", emptyList<String>(),
                errors.map { it.errorDescription })
        }
    }

    fun testRuleLandsInTheTargetAndLeavesTheSource() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Moved" On Policy.state { Assert true }
        """.trimIndent())
        val b = file("b.rules", "Namespace Policy")

        WriteCommandAction.runWriteCommandAction(project) { KrakenRuleMover.move(project, ruleIn(a, "Moved"), b) }

        assertTrue("la règle doit être dans la destination", b.text.contains("""Rule "Moved""""))
        assertFalse("et avoir quitté la source", a.text.contains("""Rule "Moved""""))
        assertParses(a, b)
    }

    /** Le point qui compte : la référence n'a pas bougé et résout toujours. */
    fun testReferencesStillResolveWhenTheDestinationIsVisible() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Moved" On Policy.state { Assert true }
        """.trimIndent())
        val b = file("b.rules", "Namespace Policy")
        val ep = file("ep.rules", """
            Namespace Policy
            EntryPoint "Validation" { "Moved" }
        """.trimIndent())

        assertNotNull("résout avant", refIn(ep, "Moved").reference.resolve())
        WriteCommandAction.runWriteCommandAction(project) { KrakenRuleMover.move(project, ruleIn(a, "Moved"), b) }
        assertNotNull("doit résoudre après", refIn(ep, "Moved").reference.resolve())
        assertParses(a, b, ep)
    }

    /**
     * Et l'inverse, qui est le dommage que l'analyse de conflit sert à
     * annoncer : le texte de la référence est intact, mais elle ne trouve
     * plus rien.
     */
    fun testReferencesStopResolvingWhenTheDestinationIsOutOfSight() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Moved" On Policy.state { Assert true }
        """.trimIndent())
        val far = file("far.rules", "Namespace Unrelated")
        val ep = file("ep.rules", """
            Namespace Policy
            EntryPoint "Validation" { "Moved" }
        """.trimIndent())

        assertNotNull("résout avant", refIn(ep, "Moved").reference.resolve())
        WriteCommandAction.runWriteCommandAction(project) { KrakenRuleMover.move(project, ruleIn(a, "Moved"), far) }
        assertEquals("le texte de la référence est inchangé", "Moved", refIn(ep, "Moved").ruleName)
        assertNull("mais elle ne résout plus", refIn(ep, "Moved").reference.resolve())
    }

    /** Après déplacement, l'index de stubs doit retrouver la règle chez elle. */
    fun testTheStubIndexFindsTheRuleInItsNewHome() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Moved" On Policy.state { Assert true }
        """.trimIndent())
        val b = file("b.rules", "Namespace Policy")

        WriteCommandAction.runWriteCommandAction(project) { KrakenRuleMover.move(project, ruleIn(a, "Moved"), b) }

        val found = com.kraken.plugin.psi.KrakenPsiUtil.findRulesVisible(b, "Moved")
        assertEquals("une seule déclaration, dans la destination", 1, found.size)
        assertEquals("b.rules", found.first().containingFile.name)
    }

    /** Déplacer vers son propre fichier ne fait rien. */
    fun testMovingIntoTheSameFileIsARefusal() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Stay" On Policy.state { Assert true }
        """.trimIndent())
        val before = a.text
        WriteCommandAction.runWriteCommandAction(project) { KrakenRuleMover.move(project, ruleIn(a, "Stay"), a) }
        assertEquals(before, a.text)
    }
}
