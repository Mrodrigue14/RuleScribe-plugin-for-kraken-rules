package com.kraken.plugin.lang

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.kraken.plugin.parser.KrakenTypes

class KrakenBraceMatcher : PairedBraceMatcher {

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(KrakenTypes.LBRACE, KrakenTypes.RBRACE, true),
            BracePair(KrakenTypes.LPAREN, KrakenTypes.RPAREN, false),
            BracePair(KrakenTypes.LBRACKET, KrakenTypes.RBRACKET, false),
            BracePair(KrakenTypes.QLBRACKET, KrakenTypes.RBRACKET, false)
        )
    }
}
