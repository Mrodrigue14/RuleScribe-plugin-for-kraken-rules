package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenUnresolvedIdentifierInspection

/**
 * L'inspection des identifiants inconnus.
 *
 * Sans inférence de types, le risque n'est pas de rater une erreur mais d'en
 * inventer une. La majorité de ces tests vérifient donc que l'inspection **se
 * tait** — c'est le comportement le plus coûteux à casser.
 */
class KrakenUnresolvedIdentifierTest : BasePlatformTestCase() {

    private val model = """
        Root Context Policy {
            String policyCd
            Child AddressInfo
        }

        Context AddressInfo {
            String postalCode
        }

        Context Coverage {
            Money limit
        }
    """.trimIndent()

    private fun problems(body: String): List<String> {
        myFixture.configureByText(
            "inspect.rules",
            """
            $model

            Rule "Under test" On Policy.policyCd {
                $body
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenUnresolvedIdentifierInspection())
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Reference ") }
    }

    // ------------------------------------------------------------------
    // Ce qui doit être signalé
    // ------------------------------------------------------------------

    fun testUnknownIdentifierIsReported() {
        assertEquals(listOf("Reference 'notAThing' not found"), problems("Assert notAThing > 0"))
    }

    // ------------------------------------------------------------------
    // Ce qui ne doit PAS l'être
    // ------------------------------------------------------------------

    fun testFieldOfTheTargetContextIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert policyCd != null"))
    }

    fun testContextNameIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert AddressInfo != null"))
    }

    fun testExpressionVariablesAreAccepted() {
        assertEquals(
            emptyList<String>(),
            problems("Assert every c in AddressInfo satisfies c != null")
        )
    }

    fun testSetVariableIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert set t to policyCd return t != null"))
    }

    /** Sans les types, un segment de chaîne n'est pas jugeable. */
    fun testPathSegmentsAreNeverReported() {
        assertEquals(emptyList<String>(), problems("Assert AddressInfo.whateverThisIs != null"))
    }

    fun testFunctionCallHeadIsLeftToTheOtherInspection() {
        assertEquals(emptyList<String>(), problems("Assert Round(policyCd, 2) != null"))
    }

    /**
     * Les prédicats de filtre voyaient tous leurs identifiants signalés : c'est
     * ce que la sonde contre le corpus réel de kraken-rules a révélé, et c'était
     * la totalité de ses faux positifs.
     */
    fun testFilterPredicateFieldsAreAccepted() {
        assertEquals(
            emptyList<String>(),
            problems("Assert Count(AddressInfo[postalCode != null]) = 1")
        )
    }

    /**
     * Filtre sur une tête inconnue — typiquement le contexte externe, dynamique :
     * la portée est indéterminée, donc on s'abstient au lieu de tout signaler.
     */
    fun testFilterOnADynamicHeadIsNotJudged() {
        assertEquals(
            emptyList<String>(),
            problems("Assert IsEmpty(context.additional.vehicles[model = policyCd])")
        )
    }

    /** `context` vit dans la portée globale du moteur, jamais déclaré en DSL. */
    fun testExternalContextRootIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert context != null"))
    }

    /** Sans cible `On` résoluble, il n'y a pas de portée de référence. */
    fun testRuleWithUnknownTargetIsNotJudged() {
        myFixture.configureByText(
            "unknown-target.rules",
            """
            $model

            Rule "No such context" On NotAContext.field {
                Assert anything > 0
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenUnresolvedIdentifierInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Reference ") }
        assertEquals(emptyList<String>(), reported)
    }

    /**
     * Plusieurs fichiers visibles peuvent déclarer un contexte homonyme — un
     * dépôt qui héberge plusieurs produits, ou de simples fixtures à côté du
     * code. Le champ cherché peut n'exister que dans l'une d'elles ; n'en
     * consulter qu'une, au hasard de l'ordre des fichiers, produisait un faux
     * positif sur du code parfaitement valide. Cas signalé en usage réel.
     */
    fun testFieldIsFoundAcrossHomonymousContextDeclarations() {
        myFixture.addFileToProject(
            "other-policy.rules",
            """
            Context Policy {
                String somethingElse
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "real-policy.rules",
            """
            Context Policy {
                Date effectiveDate
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "rule.rules",
            """
            Rule "Effective date past" On Policy.effectiveDate {
                Assert effectiveDate < Today()
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenUnresolvedIdentifierInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Reference ") }
        assertEquals(
            "Le champ n'existe que dans l'une des déclarations homonymes",
            emptyList<String>(),
            reported
        )
    }

    fun testFunctionParametersAreAccepted() {
        myFixture.configureByText(
            "function.rules",
            """
            $model

            Function Postal(AddressInfo info) : String {
                info.postalCode
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenUnresolvedIdentifierInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Reference ") }
        assertEquals("Un paramètre de fonction est en portée", emptyList<String>(), reported)
    }
}
