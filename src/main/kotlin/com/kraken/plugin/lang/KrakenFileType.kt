package com.kraken.plugin.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object KrakenFileType : LanguageFileType(KrakenLanguage) {
    override fun getName(): String = "Kraken Rules"
    override fun getDescription(): String = "Kraken rules DSL file"
    override fun getDefaultExtension(): String = "rules"
    override fun getIcon(): Icon = KrakenIcons.FILE
}
