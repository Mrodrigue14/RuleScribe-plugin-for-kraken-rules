package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenDeclarations
import com.kraken.plugin.psi.KrakenRuleDecl
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

    /**
     * Find Usages, the usages inlay and the unused rule inspection look up the references
     * to every rule of a file on each pass, so a lookup must not walk every file.
     */
    fun testUsageSearchScalesToFiveHundredFiles() {
        for (n in 0 until 10) {
            for (i in 0 until 50) {
                myFixture.addFileToProject(
                    "ns$n/file_${n}_$i.rules",
                    """
                    Namespace Ns$n

                    Rule "Rule ${n}_$i" On Entity.id {
                        Assert Round(id) > 0
                    }

                    EntryPoint "EP ${n}_$i" {
                        "Rule ${n}_$i"
                    }
                    """.trimIndent(),
                )
            }
        }
        val probes = myFixture.addFileToProject(
            "probes.rules",
            "Namespace Ns0\n\n" + (0 until 50).joinToString("\n") { "Rule \"Probe $it\" On Entity.id { Set Mandatory }" },
        )
        val referenced = KrakenDeclarations.findRuleVisible(probes, "Rule 0_0")!!
        assertEquals(1, referenced.visibleUsages().size)

        val start = System.nanoTime()
        for (probe in allOf<KrakenRuleDecl>(probes)) assertEmpty(probe.visibleUsages())
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("Usage search for 50 rules over 500 files: $elapsedMs ms")
        assertTrue("Usage search took $elapsedMs ms; does it walk every file again?", elapsedMs < 10_000)
    }
}
