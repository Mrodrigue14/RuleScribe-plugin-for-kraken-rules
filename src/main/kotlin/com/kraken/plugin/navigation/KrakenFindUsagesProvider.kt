package com.kraken.plugin.navigation

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet
import com.kraken.plugin.lang.KrakenParserDefinition
import com.kraken.plugin.parser.KrakenLexer
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenDeclaration

class KrakenFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        KrakenLexer(),
        TokenSet.create(KrakenTypes.IDENTIFIER),
        KrakenParserDefinition.COMMENTS,
        TokenSet.create(KrakenTypes.STRING, KrakenTypes.NUMBER_LIT),
    )

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean = psiElement is PsiNamedElement

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = "Kraken " + ((element as? KrakenDeclaration)?.kind?.typeText ?: "element")

    override fun getDescriptiveName(element: PsiElement): String = (element as? PsiNamedElement)?.name ?: element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
}
