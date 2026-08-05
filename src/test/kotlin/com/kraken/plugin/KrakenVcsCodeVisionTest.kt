package com.kraken.plugin

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.navigation.KrakenVcsCodeVisionContext
import com.kraken.plugin.parser.KrakenTypes

/**
 * Quels éléments reçoivent l'inlay « auteur, date », et jusqu'où va leur bloc.
 *
 * L'inlay lui-même dépend d'un dépôt sous contrôle de version et
 * d'annotations disponibles, hors de portée d'un test headless. Ce qui *est*
 * testable, et ce qui nous appartient, c'est la sélection des éléments et la
 * reconnaissance de l'accolade fermante dont dépend le calcul d'étendue.
 */
class KrakenVcsCodeVisionTest : BasePlatformTestCase() {

    private val context = KrakenVcsCodeVisionContext()

    private fun configure() = myFixture.configureByText(
        "annotated.rules",
        """
        Namespace Policy

        Context Coverage {
            Money limitAmount
        }

        Function TotalLimit(Coverage[] coverages) : Number {
            Sum(coverages.limitAmount)
        }

        Rule "Limit is positive" On Coverage.limitAmount {
            Assert limitAmount > 0
        }

        EntryPoint "Validation" {
            "Limit is positive"
        }
        """.trimIndent()
    )

    private fun accepted(): List<String> {
        val found = mutableListOf<String>()
        PsiTreeUtil.processElements(myFixture.file) { element ->
            if (context.isAccepted(element)) found += element.node.elementType.toString()
            true
        }
        return found.sorted()
    }

    fun testEveryTopLevelDeclarationIsAccepted() {
        configure()
        assertEquals(
            listOf("CONTEXT_DECL", "ENTRY_POINT_DECL", "FUNCTION_DECL", "RULE_DECL"),
            accepted()
        )
    }

    /** Les références et les corps ne sont pas des déclarations : pas d'inlay. */
    fun testNonDeclarationsAreRejected() {
        configure()
        val rejected = listOf(
            KrakenTypes.RULE_REF, KrakenTypes.RULE_BODY,
            KrakenTypes.FUNCTION_CALL, KrakenTypes.DIMENSION_DECL
        )
        PsiTreeUtil.processElements(myFixture.file) { element ->
            if (element.node.elementType in rejected) {
                assertFalse(
                    "${element.node.elementType} must not carry an author inlay",
                    context.isAccepted(element)
                )
            }
            true
        }
    }

    /**
     * La classe de base déduit l'étendue du bloc de l'accolade fermante. Si
     * celle-ci n'était pas reconnue, l'étendue s'arrêterait avant la fin de la
     * déclaration et l'inlay annoncerait l'auteur de la mauvaise portion de
     * fichier — d'où cette vérification sur le résultat plutôt que sur le
     * prédicat, qui est protégé.
     */
    fun testEffectiveRangeCoversTheWholeDeclaration() {
        val file = configure()
        val rule = PsiTreeUtil.findChildrenOfType(file, com.kraken.plugin.psi.KrakenRuleDecl::class.java).single()

        val range = context.computeEffectiveRange(rule)
        // La base démarre l'étendue à `textOffset`, que KrakenRuleDecl fait
        // pointer sur le nom : l'inlay décrit ainsi l'auteur de la règle, pas
        // celui d'une annotation @Dimension qui la précéderait.
        assertEquals("Commence au nom de la règle", rule.nameIdentifier!!.textOffset, range.startOffset)
        // La base s'arrête à la fin de la dernière ligne du corps : la ligne de
        // l'accolade fermante est exclue, sinon l'auteur affiché serait souvent
        // celui qui a simplement ajouté la dernière instruction.
        assertTrue("L'étendue reste dans la règle", rule.textRange.contains(range))
        assertTrue(
            "L'étendue doit contenir tout le corps de la règle",
            file.text.substring(range.startOffset, range.endOffset).contains("Assert limitAmount > 0")
        )
    }
}
