package com.kraken.plugin

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Lisibilité des erreurs de syntaxe.
 *
 * Grammar-Kit compose ces messages en énumérant tout ce qui pouvait suivre. Sur
 * une expression KEL cassée, cela donnait plus de quatre cents caractères :
 * chaque token préfixé de `KrakenTokenType.`, et les treize opérateurs binaires
 * listés un par un. Un message que personne ne lit ne signale rien.
 */
class KrakenParseErrorMessageTest : BasePlatformTestCase() {

    private fun errors(source: String): List<String> {
        myFixture.configureByText("err.rules", source)
        return PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
            .map { it.errorDescription }
    }

    private val brokenExpression = """
        Rule "R" On Policy.state {
            Assert Round(limit] > 0
        }
    """.trimIndent()

    /** Le nom de la classe n'a rien à faire dans un message d'erreur. */
    fun testTokenNamesCarryNoClassPrefix() {
        val message = errors(brokenExpression).first()
        assertFalse("« KrakenTokenType. » ne doit plus apparaître : $message",
            message.contains("KrakenTokenType"))
    }

    /**
     * Les treize opérateurs binaires se présentent groupés. Sans cela, chacun
     * apparaît nommément et le message triple de longueur.
     */
    fun testBinaryOperatorsAreReportedAsOneGroup() {
        val message = errors(brokenExpression).first()
        assertTrue("les opérateurs doivent être groupés : $message", message.contains("<operator>"))
        for (operator in listOf("instanceof", "satisfies", "Matches")) {
            assertFalse("'$operator' ne doit plus être listé séparément : $message",
                message.contains(operator))
        }
    }

    /**
     * Seuil volontairement lâche : il ne s'agit pas de figer une formulation,
     * mais d'empêcher qu'une évolution de la grammaire ramène les messages
     * fleuves d'avant (plus de 400 caractères).
     */
    fun testMessagesStayReadableInLength() {
        for (message in errors(brokenExpression)) {
            assertTrue("message trop long (${message.length}) : $message", message.length < 150)
        }
    }

    /** Le message doit toujours dire ce qu'il a trouvé, pas seulement ce qu'il attendait. */
    fun testMessageStillNamesTheOffendingToken() {
        assertTrue(errors(brokenExpression).first().contains("got ']'"))
    }
}
