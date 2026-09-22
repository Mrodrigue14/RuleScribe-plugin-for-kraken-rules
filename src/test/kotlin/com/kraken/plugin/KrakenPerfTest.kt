package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenRuleRef

/**
 * Resolution stays correct and fast on a synthetic 500-file project. Without the
 * namespace model cache, every resolution re-read every file's AST (quadratic cost);
 * this guards against losing that cache.
 */
class KrakenPerfTest : BasePlatformTestCase() {

    fun testResolutionScalesToFiveHundredFiles() {
        val namespaces = 10
        val perNamespace = 50

        for (n in 0 until namespaces) {
            for (i in 0 until perNamespace) {
                myFixture.addFileToProject(
                    "ns$n/rule_${n}_$i.rules",
                    """
                    Namespace Ns$n

                    Rule "Rule ${n}_$i" On Entity.id {
                        Set Mandatory
                    }
                    """.trimIndent(),
                )
            }
        }

        // Consumer in Ns0, referencing a rule of its own namespace.
        myFixture.configureByText(
            "consumer.rules",
            """
            Namespace Ns0

            EntryPoint "EP" {
                "Rule 0_0"
            }
            """.trimIndent(),
        )

        val ref = allOf<KrakenRuleRef>(myFixture.file)
            .firstOrNull { it.ruleName == "Rule 0_0" }
        assertNotNull("The 'Rule 0_0' reference must exist in the PSI", ref)

        assertNotNull(
            "The rule must resolve across 500 files",
            ref!!.reference.resolve(),
        )

        // The cache must make repeated resolutions near-instant.
        val start = System.nanoTime()
        repeat(300) { assertNotNull(ref.reference.resolve()) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // A loose bound that tolerates CI variance but fails on an O(n) regression per
        // resolution (re-reading the 500 files each time).
        assertTrue(
            "300 resolutions over 500 files took $elapsedMs ms; " +
                "did the namespace model cache regress?",
            elapsedMs < 10_000,
        )
    }
}
