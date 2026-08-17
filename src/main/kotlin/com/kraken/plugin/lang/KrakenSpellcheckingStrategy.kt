package com.kraken.plugin.lang

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer
import com.kraken.plugin.parser.KrakenTypes

/**
 * Vérifie l'orthographe des seules chaînes destinées à être lues par un
 * humain, et des commentaires.
 *
 * Un `.rules` est plein de chaînes, mais la plupart sont des identifiants :
 * noms de règles, d'entry points, de dimensions, codes d'erreur. Tout vérifier
 * signalerait `AZStateCoverateVisibility` et `policyCd` à chaque ligne, ce qui
 * noierait les vraies fautes — la vérification deviendrait inutilisable et
 * serait désactivée.
 *
 * Deux positions sont de la prose, d'après la grammaire :
 *
 * - `description_clause ::= DESCRIPTION_KW STRING`
 * - `payload_message ::= message_severity STRING (COLON STRING)?`, où la
 *   **dernière** chaîne est le message. Avec deux chaînes, la première est le
 *   code d'erreur (`Error "code" : "message"`), qui n'est pas de la prose.
 */
class KrakenSpellcheckingStrategy : SpellcheckingStrategy() {

    override fun getTokenizer(element: PsiElement): Tokenizer<*> = when {
        element.node?.elementType in COMMENTS -> TEXT_TOKENIZER
        element.node?.elementType == KrakenTypes.STRING && isProse(element) -> QUOTED_TEXT
        else -> EMPTY_TOKENIZER
    }

    private fun isProse(string: PsiElement): Boolean = when (string.parent?.node?.elementType) {
        KrakenTypes.DESCRIPTION_CLAUSE -> true
        KrakenTypes.PAYLOAD_MESSAGE -> string === lastString(string.parent)
        else -> false
    }

    private fun lastString(parent: PsiElement): PsiElement? =
        parent.node.getChildren(null).lastOrNull { it.elementType == KrakenTypes.STRING }?.psi

    private companion object {
        val COMMENTS = setOf(
            KrakenTypes.LINE_COMMENT,
            KrakenTypes.BLOCK_COMMENT,
            KrakenTypes.DOC_COMMENT,
        )

        /** Sans les guillemets, que le découpeur ne saurait pas ignorer. */
        val QUOTED_TEXT = object : Tokenizer<PsiElement>() {
            override fun tokenize(element: PsiElement, consumer: TokenConsumer) {
                val text = element.text
                if (text.length < 3) return
                consumer.consumeToken(
                    element, text, false, 0,
                    TextRange(1, text.length - 1),
                    PlainTextSplitter.getInstance()
                )
            }
        }
    }
}
