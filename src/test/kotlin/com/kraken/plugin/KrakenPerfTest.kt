package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenRuleRef

/**
 * Vérifie que la résolution reste correcte ET rapide sur un projet synthétique
 * de 500 fichiers. Sans le cache du modèle de namespaces, chaque résolution
 * relisait l'AST de tous les fichiers — coût quadratique. Ce test garde contre
 * une régression de ce cache.
 */
class KrakenPerfTest : BasePlatformTestCase() {

    fun testResolutionScalesToFiveHundredFiles() {
        val namespaces = 10
        val perNamespace = 50   // 10 x 50 = 500 fichiers

        for (n in 0 until namespaces) {
            for (i in 0 until perNamespace) {
                myFixture.addFileToProject(
                    "ns$n/rule_${n}_$i.rules",
                    """
                    Namespace Ns$n

                    Rule "Rule ${n}_$i" On Entity.id {
                        Set Mandatory
                    }
                    """.trimIndent()
                )
            }
        }

        // Consommateur dans Ns0 : référence une règle de son propre namespace.
        myFixture.configureByText(
            "consumer.rules",
            """
            Namespace Ns0

            EntryPoint "EP" {
                "Rule 0_0"
            }
            """.trimIndent()
        )

        val ref = PsiTreeUtil.findChildrenOfType(myFixture.file, KrakenRuleRef::class.java)
            .firstOrNull { it.ruleName == "Rule 0_0" }
        assertNotNull("La référence 'Rule 0_0' doit exister dans le PSI", ref)

        assertNotNull(
            "La règle doit se résoudre à l'échelle de 500 fichiers",
            ref!!.reference.resolve()
        )

        // Le cache doit rendre les résolutions répétées quasi instantanées.
        val start = System.nanoTime()
        repeat(300) { assertNotNull(ref.reference.resolve()) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // Borne large pour tolérer la variance CI, mais qui échouerait en cas de
        // régression O(n) par résolution (relecture des 500 fichiers à chaque fois).
        assertTrue(
            "300 résolutions sur 500 fichiers ont pris $elapsedMs ms — " +
                "le cache du modèle de namespaces a-t-il régressé ?",
            elapsedMs < 10_000
        )
    }
}
