package com.kraken.plugin.lang

import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenPsiUtil

/**
 * Spellchecks comments and only the strings meant to be read by people.
 *
 * Most strings in a `.rules` file are identifiers: rule, entry point and dimension
 * names, error codes. Checking all of them would flag `AZStateCoverateVisibility` and
 * `policyCd` on every line and bury the real typos.
 *
 * Two grammar positions hold prose:
 *
 * - `description_clause ::= DESCRIPTION_KW STRING`
 * - `payload_message ::= message_severity STRING (COLON STRING)?`, where the last
 *   string is the message. With two strings, the first is the error code
 *   (`Error "code" : "message"`).
 */
class KrakenSpellcheckingStrategy : SpellcheckingStrategy() {

    override fun getTokenizer(element: PsiElement): Tokenizer<*> = when {
        element.node?.elementType in KrakenParserDefinition.COMMENTS -> TEXT_TOKENIZER
        element.node?.elementType == KrakenTypes.STRING && isProse(element) -> QUOTED_TEXT
        else -> EMPTY_TOKENIZER
    }

    private fun isProse(string: PsiElement): Boolean = when (string.parent?.node?.elementType) {
        KrakenTypes.DESCRIPTION_CLAUSE -> true
        KrakenTypes.PAYLOAD_MESSAGE -> string === lastString(string.parent)
        else -> false
    }

    private fun lastString(parent: PsiElement): PsiElement? = parent.node.getChildren(null).lastOrNull { it.elementType == KrakenTypes.STRING }?.psi

    private companion object {
        /** Excludes the quotes, which the splitter would not ignore. */
        val QUOTED_TEXT = object : Tokenizer<PsiElement>() {
            override fun tokenize(element: PsiElement, consumer: TokenConsumer) {
                val text = element.text
                if (text.length < 3) return
                consumer.consumeToken(
                    element,
                    text,
                    false,
                    0,
                    KrakenPsiUtil.insideQuotes(0, text.length),
                    PlainTextSplitter.getInstance(),
                )
            }
        }
    }
}
