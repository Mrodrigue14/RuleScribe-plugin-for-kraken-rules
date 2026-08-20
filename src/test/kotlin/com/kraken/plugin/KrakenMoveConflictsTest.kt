package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.refactoring.KrakenMoveConflicts

/**
 * L'analyse qui décide si déplacer une règle casse quelque chose.
 *
 * Les assertions portent sur la **résolution**, jamais sur le texte : c'est
 * tout l'intérêt du calcul. Une référence Kraken nomme une règle, pas un
 * fichier, donc un déplacement ne modifie aucun texte de référence — il
 * modifie seulement si ces références trouvent encore leur cible.
 */
class KrakenMoveConflictsTest : BasePlatformTestCase() {

    private fun file(name: String, text: String): KrakenFile =
        myFixture.addFileToProject(name, text) as KrakenFile

    private fun ruleIn(file: KrakenFile, name: String): KrakenRuleDecl =
        PsiTreeUtil.findChildrenOfType(file, KrakenRuleDecl::class.java).first { it.name == name }

    /** Même namespace : rien ne peut casser. */
    fun testMovingWithinTheSameNamespaceIsSafe() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
        """.trimIndent())
        val b = file("b.rules", "Namespace Policy")
        file("ep.rules", """
            Namespace Policy
            EntryPoint "Validation" { "Shared" }
        """.trimIndent())

        assertEquals(emptyList<Any>(), KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), b))
    }

    /** Le cas qui motive l'analyse : la destination sort de la portée du référent. */
    fun testMovingOutOfSightIsReported() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
        """.trimIndent())
        val far = file("far.rules", "Namespace Unrelated")
        file("ep.rules", """
            Namespace Policy
            EntryPoint "Validation" { "Shared" }
        """.trimIndent())

        val broken = KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), far)
        assertEquals("la référence de l'EntryPoint doit être signalée", 1, broken.size)
        assertEquals("Shared", broken.first().ruleName)
    }

    /** Un `Include` vers la destination suffit à préserver la résolution. */
    fun testIncludingTheDestinationKeepsItResolving() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
        """.trimIndent())
        val other = file("other.rules", "Namespace Base")
        file("ep.rules", """
            Namespace Policy
            Include Base
            EntryPoint "Validation" { "Shared" }
        """.trimIndent())

        assertEquals(emptyList<Any>(), KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), other))
    }

    /**
     * `Import Rule` est un axe distinct d'`Include`. Un import qui nomme déjà
     * le namespace de destination reste valide après le déplacement.
     */
    fun testAnImportNamingTheDestinationKeepsItResolving() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
        """.trimIndent())
        val other = file("other.rules", "Namespace Base")
        file("ep.rules", """
            Namespace Consumer
            Import Rule "Shared" From Base
            EntryPoint "Validation" { "Shared" }
        """.trimIndent())

        assertEquals(emptyList<Any>(), KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), other))
    }

    /**
     * L'inverse, et c'est le piège : un import qui nomme l'**ancien**
     * namespace pointe, après le déplacement, vers un namespace qui ne
     * contient plus la règle.
     */
    fun testAnImportNamingTheOldNamespaceBreaks() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
        """.trimIndent())
        val other = file("other.rules", "Namespace Base")
        file("ep.rules", """
            Namespace Consumer
            Import Rule "Shared" From Policy
            EntryPoint "Validation" { "Shared" }
        """.trimIndent())

        val broken = KrakenMoveConflicts.brokenBy(ruleIn(a, "Shared"), other)
        assertEquals("l'import pointe vers l'ancien namespace : $broken", 1, broken.size)
    }

    /** Une règle que personne ne référence se déplace sans risque. */
    fun testAnUnreferencedRuleHasNoConflicts() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Lonely" On Policy.state { Assert true }
        """.trimIndent())
        val far = file("far.rules", "Namespace Unrelated")

        assertEquals(emptyList<Any>(), KrakenMoveConflicts.brokenBy(ruleIn(a, "Lonely"), far))
    }
}
