package com.kraken.plugin

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenDuplicateRuleInspection
import com.kraken.plugin.inspection.KrakenUndeclaredDimensionInspection

/**
 * Correctifs des inspections.
 *
 * On vérifie le texte obtenu, pas seulement qu'un correctif est proposé :
 * c'est le résultat qui doit rester du Kraken valide, notamment l'ordre imposé
 * par `kraken_file ::= namespace_decl? import_decl* model_item*`.
 */
class KrakenQuickFixTest : BasePlatformTestCase() {

    /**
     * La plateforme enveloppe un `LocalQuickFix` dans une action dont le
     * `familyName` porte le libellé affiché, donc `getName()` quand le
     * correctif en définit un : on cherche par préfixe.
     */
    private fun applyFix(fileName: String, before: String, label: String): String {
        myFixture.configureByText(fileName, before)
        val fix = myFixture.getAllQuickFixes().firstOrNull { it.familyName.startsWith(label) }
            ?: error("correctif '$label' absent, disponibles: " +
                myFixture.getAllQuickFixes().map { it.familyName })
        myFixture.launchAction(fix)
        val after = myFixture.file.text
        // Le correctif écrit du texte : la seule garantie qui compte est que
        // le fichier parse toujours.
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        assertEquals("le correctif a cassé le fichier :\n$after", emptyList<String>(),
            errors.map { it.errorDescription })
        return after
    }

    // ------------------------------------------------------------------
    // Déclarer une dimension manquante
    // ------------------------------------------------------------------

    fun testDeclaresMissingDimensionAfterTheExistingOnes() {
        myFixture.enableInspections(KrakenUndeclaredDimensionInspection())
        val after = applyFix(
            "dims.rules",
            """
            Dimension "planCd" : String

            @Dimension("packageCd", "Pizza")
            Rule "R" On Policy.state {
                Assert true
            }
            """.trimIndent(),
            "Declare dimension"
        )
        assertTrue(
            "les dimensions restent groupées, une par ligne :\n$after",
            after.contains("Dimension \"planCd\" : String\nDimension \"packageCd\" : String")
        )
    }

    /**
     * Une dimension ne peut pas précéder le `Namespace` : le correctif doit
     * l'insérer après l'en-tête, sinon il produit un fichier qui ne parse plus.
     */
    fun testDeclaredDimensionGoesAfterTheHeader() {
        myFixture.enableInspections(KrakenUndeclaredDimensionInspection())
        val after = applyFix(
            "ns.rules",
            """
            Namespace Policy
            Include Base

            Dimension "planCd" : String

            @Dimension("packageCd", "Pizza")
            Rule "R" On Policy.state {
                Assert true
            }
            """.trimIndent(),
            "Declare dimension"
        )
        val namespaceAt = after.indexOf("Namespace Policy")
        val newDimensionAt = after.indexOf("Dimension \"packageCd\"")
        assertTrue("la dimension doit suivre le Namespace :\n$after", namespaceAt < newDimensionAt)
        assertTrue(after.indexOf("Include Base") < newDimensionAt)
    }

    // ------------------------------------------------------------------
    // Annoter une règle dupliquée
    // ------------------------------------------------------------------

    fun testAddsDimensionAnnotationToADuplicateRule() {
        myFixture.enableInspections(KrakenDuplicateRuleInspection())
        val after = applyFix(
            "dup.rules",
            """
            Rule "Same" On Policy.state {
                Assert true
            }

            Rule "Same" On Policy.state {
                Assert false
            }
            """.trimIndent(),
            "Add a differentiating @Dimension annotation"
        )
        assertTrue(
            "l'annotation précède la règle :\n$after",
            after.contains("@Dimension(\"dimensionName\", \"value\")\nRule \"Same\"")
        )
    }

    /** Une règle imbriquée dans un `Rules { }` garde son indentation. */
    fun testAnnotationFollowsTheRuleIndentation() {
        myFixture.enableInspections(KrakenDuplicateRuleInspection())
        val after = applyFix(
            "nested.rules",
            """
            Rules {
                Rule "Same" On Policy.state {
                    Assert true
                }

                Rule "Same" On Policy.state {
                    Assert false
                }
            }
            """.trimIndent(),
            "Add a differentiating @Dimension annotation"
        )
        assertTrue(
            "l'annotation reprend l'indentation de la règle :\n$after",
            after.contains("    @Dimension(\"dimensionName\", \"value\")\n    Rule \"Same\"")
        )
    }
}
