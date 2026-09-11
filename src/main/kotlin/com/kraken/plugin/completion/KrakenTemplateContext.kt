package com.kraken.plugin.completion

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.kraken.plugin.lang.KrakenFile

class KrakenTemplateContext : TemplateContextType("Kraken") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean = templateActionContext.file is KrakenFile
}
