package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.refactoring.KrakenMoveConflicts

/**
 * L'analyse qui décide si déplacer un EntryPoint casse quelque chose.
 *
 * Une règle n'a qu'une direction : qui la référence. Un EntryPoint en a deux,
 * parce qu'il référence lui-même, et c'est la seconde qu'un portage naïf de
 * Move rule oublierait — un entry point posé dans un namespace qui ne voit pas
 * ses propres règles devient vide sans qu'une ligne bouge à l'intérieur.
 */
class KrakenEntryPointMoveConflictsTest : BasePlatformTestCase() {

    private fun file(name: String, text: String): KrakenFile =
        myFixture.addFileToProject(name, text) as KrakenFile

    private fun epIn(file: KrakenFile, name: String): KrakenEntryPointDecl =
        PsiTreeUtil.findChildrenOfType(file, KrakenEntryPointDecl::class.java).first { it.name == name }

    // ------------------------------------------------------------------
    // Sens entrant : qui référence l'EntryPoint déplacé
    // ------------------------------------------------------------------

    /** Même namespace : rien ne peut casser, dans aucun des deux sens. */
    fun testMovingWithinTheSameNamespaceIsSafe() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Shared" On Policy.state { Assert true }
            EntryPoint "Inner" { "Shared" }
        """.trimIndent())
        val b = file("b.rules", "Namespace Policy")
        file("outer.rules", """
            Namespace Policy
            EntryPoint "Outer" { EntryPoint "Inner" }
        """.trimIndent())

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Inner"), b)
        assertTrue("aucun conflit attendu, obtenu $broken", broken.isEmpty)
    }

    /** Un `EntryPoint "Inner"` ailleurs cesse de voir la déclaration déplacée. */
    fun testIncomingReferenceThatLosesSightIsReported() {
        val a = file("a.rules", """
            Namespace Policy
            EntryPoint "Inner" { }
        """.trimIndent())
        val far = file("far.rules", "Namespace Other")
        file("outer.rules", """
            Namespace Policy
            EntryPoint "Outer" { EntryPoint "Inner" }
        """.trimIndent())

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Inner"), far)
        assertEquals(1, broken.incoming.size)
        assertEquals(emptyList<Any>(), broken.outgoing)
    }

    /** La destination reste visible via `Include` : rien à signaler. */
    fun testIncludeKeepsTheIncomingReferenceAlive() {
        val a = file("a.rules", """
            Namespace Policy
            EntryPoint "Inner" { }
        """.trimIndent())
        val shared = file("shared.rules", "Namespace Shared")
        file("outer.rules", """
            Namespace Policy
            Include Shared
            EntryPoint "Outer" { EntryPoint "Inner" }
        """.trimIndent())

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Inner"), shared)
        assertTrue("Include devrait suffire, obtenu $broken", broken.isEmpty)
    }

    // ------------------------------------------------------------------
    // Sens sortant : ce que l'EntryPoint déplacé référence
    // ------------------------------------------------------------------

    /** Le cas qu'une règle n'a pas : l'entry point part sans ses propres règles. */
    fun testOwnRuleItemThatStopsResolvingIsReported() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
        """.trimIndent())
        val far = file("far.rules", "Namespace Other")

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertEquals(emptyList<Any>(), broken.incoming)
        assertEquals(1, broken.outgoing.size)
    }

    /**
     * La branche qu'une implémentation plus simple rate : un `Import Rule` du
     * namespace de **destination** rattrape l'item.
     */
    fun testImportAtTheDestinationRescuesTheOwnItem() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
        """.trimIndent())
        val far = file("far.rules", """
            Namespace Other
            Import Rule "Stays" From Policy
        """.trimIndent())

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertTrue("l'import de destination devrait sauver l'item, obtenu $broken", broken.isEmpty)
    }

    /** Un item imbriqué n'a que l'axe de visibilité, aucun import ne le rattrape. */
    fun testOwnNestedEntryPointItemThatStopsResolvingIsReported() {
        val a = file("a.rules", """
            Namespace Policy
            EntryPoint "Leaf" { }
            EntryPoint "Moving" { EntryPoint "Leaf" }
        """.trimIndent())
        val far = file("far.rules", "Namespace Other")

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertEquals(1, broken.outgoing.size)
    }

    /** Un item qui ne résolvait déjà pas n'est cassé par personne. */
    fun testItemThatAlreadyFailsToResolveIsNotCounted() {
        val a = file("a.rules", """
            Namespace Policy
            EntryPoint "Moving" { "Nowhere" }
        """.trimIndent())
        val far = file("far.rules", "Namespace Other")

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertEquals(emptyList<Any>(), broken.outgoing)
    }

    /** Les deux sens se comptent séparément, parce qu'ils ne s'arbitrent pas pareil. */
    fun testBothDirectionsAreCountedApart() {
        val a = file("a.rules", """
            Namespace Policy
            Rule "Stays" On Policy.state { Assert true }
            EntryPoint "Moving" { "Stays" }
        """.trimIndent())
        val far = file("far.rules", "Namespace Other")
        file("outer.rules", """
            Namespace Policy
            EntryPoint "Outer" { EntryPoint "Moving" }
        """.trimIndent())

        val broken = KrakenMoveConflicts.brokenBy(epIn(a, "Moving"), far)
        assertEquals(1, broken.incoming.size)
        assertEquals(1, broken.outgoing.size)
        assertEquals(2, broken.total)
    }
}
