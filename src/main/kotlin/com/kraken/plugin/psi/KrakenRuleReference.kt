package com.kraken.plugin.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

class KrakenRuleReference(element: KrakenRuleRef) : PsiReferenceBase<KrakenRuleRef>(element, KrakenPsiUtil.insideQuotes(0, element.textLength)) {

    override fun resolve(): PsiElement? = KrakenPsiUtil.findRuleVisible(element, element.ruleName)

    override fun getVariants(): Array<Any> = KrakenPsiUtil.findRulesVisible(element)
        .mapNotNull { it.name }
        .distinct()
        .toTypedArray()
}
