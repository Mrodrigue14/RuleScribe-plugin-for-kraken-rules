package com.kraken.plugin.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

class KrakenRuleReference(element: KrakenRuleRef) : PsiReferenceBase<KrakenRuleRef>(element, rangeInside(element)) {

    override fun resolve(): PsiElement? = KrakenPsiUtil.findRuleVisible(element, element.ruleName)

    override fun getVariants(): Array<Any> = KrakenPsiUtil.findRulesVisible(element)
        .mapNotNull { it.name }
        .distinct()
        .toTypedArray()

    companion object {
        private fun rangeInside(element: KrakenRuleRef): TextRange {
            val length = element.textLength
            return if (length >= 2) TextRange(1, length - 1) else TextRange(0, length)
        }
    }
}
