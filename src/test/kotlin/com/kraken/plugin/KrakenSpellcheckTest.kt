package com.kraken.plugin

import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Orthographe.
 *
 * Les messages d'erreur d'un `.rules` finissent affichés à l'utilisateur final
 * d'une application, d'où la vérification. Les tests négatifs comptent autant :
 * un vocabulaire métier signalé à tort noierait les vraies fautes.
 */
class KrakenSpellcheckTest : BasePlatformTestCase() {

    private fun typos(source: String): List<String> {
        myFixture.configureByText("spell.rules", source)
        myFixture.enableInspections(SpellCheckingInspection())
        return myFixture.doHighlighting()
            .filter { it.description?.contains("Typo", ignoreCase = true) == true }
            .map { myFixture.file.text.substring(it.startOffset, it.endOffset) }
    }

    fun testATypoInAnErrorMessageIsReported() {
        assertTrue(
            "la faute doit être signalée",
            typos(
                """
                Rule "R" On Policy.state {
                    Assert true
                    Error "code" : "Limit amount is mandatoryy"
                }
                """.trimIndent()
            ).contains("mandatoryy")
        )
    }

    fun testATypoInACommentIsReported() {
        assertTrue(
            typos(
                """
                // this rule is deliberatelly wrong
                Rule "R" On Policy.state {
                    Assert true
                }
                """.trimIndent()
            ).contains("deliberatelly")
        )
    }

    /** Un message correct ne doit rien déclencher. */
    fun testACleanMessageIsNotReported() {
        assertEquals(
            emptyList<String>(),
            typos(
                """
                Rule "R" On Policy.state {
                    Assert true
                    Error "code" : "Limit amount is mandatory"
                }
                """.trimIndent()
            )
        )
    }

    /**
     * `Error "code" : "message"` : le code est un identifiant, pas de la
     * prose. Seule la seconde chaîne est vérifiée.
     */
    fun testTheErrorCodeIsNotCheckedButTheMessageIs() {
        val reported = typos(
            """
            Rule "R" On Policy.state {
                Assert true
                Error "limitAmountMandatoryy" : "Limit amount is requiredd"
            }
            """.trimIndent()
        )
        assertTrue("le message doit être vérifié : $reported", reported.contains("requiredd"))
        assertFalse("le code ne doit pas l'être : $reported", reported.contains("Mandatoryy"))
    }

    /**
     * Le vocabulaire métier d'un modèle Kraken — `policyCd`,
     * `AutoCOMPCoverage` — n'est dans aucun dictionnaire. Le signaler
     * rendrait la vérification inutilisable.
     */
    fun testIdentifiersAndRuleNamesAreNotChecked() {
        assertEquals(
            emptyList<String>(),
            typos(
                """
                Context AutoCOMPCoverage {
                    String policyCd
                }

                Rule "AZStateCoverateVisibility" On AutoCOMPCoverage.policyCd {
                    Assert policyCd != null
                }
                """.trimIndent()
            )
        )
    }
}
