package com.kraken.plugin.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.kraken.plugin.parser.KrakenTypes

class KrakenRuleRefManipulator : AbstractElementManipulator<KrakenRuleRef>() {

    override fun handleContentChange(element: KrakenRuleRef, range: TextRange, newContent: String): KrakenRuleRef {
        KrakenPsiUtil.replaceQuoted(element.node.findChildByType(KrakenTypes.STRING), newContent)
        return element
    }

    override fun getRangeInElement(element: KrakenRuleRef): TextRange = KrakenPsiUtil.insideQuotes(0, element.textLength)
}
