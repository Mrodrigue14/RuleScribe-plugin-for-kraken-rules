package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.kraken.plugin.parser.KrakenTypes

/** Nested `EntryPoint "name"` item, referencing that entry point's declaration. */
class KrakenEpRef(node: ASTNode) : ASTWrapperPsiElement(node) {

    val entryPointName: String?
        get() = node.findChildByType(KrakenTypes.STRING)?.text?.let(StringUtil::unquoteString)

    override fun getReference(): PsiReference? {
        val range = stringRangeInside(this) ?: return null
        return KrakenEntryPointReference(this, range)
    }

    /** Tells identical references apart in navigation popups. */
    override fun getPresentation(): ItemPresentation = KrakenPresentations.of(
        this,
        KrakenPresentations.containerText(this, entryPointName?.let { "\"$it\"" }),
        KrakenPresentations.ENTRY_POINT_ICON,
    )

    companion object {
        fun stringRangeInside(element: KrakenEpRef): TextRange? {
            val leaf = element.node.findChildByType(KrakenTypes.STRING) ?: return null
            return KrakenPsiUtil.insideQuotes(leaf.startOffset - element.node.startOffset, leaf.textLength)
        }
    }
}

class KrakenEntryPointReference(element: KrakenEpRef, range: TextRange) : PsiReferenceBase<KrakenEpRef>(element, range) {

    override fun resolve(): PsiElement? {
        val name = element.entryPointName ?: return null
        return KrakenPsiUtil.findEntryPointVisible(element, name)
    }

    override fun getVariants(): Array<Any> = KrakenPsiUtil.findEntryPointsVisible(element)
        .mapNotNull { it.name }
        .distinct()
        .toTypedArray()
}

class KrakenEpRefManipulator : AbstractElementManipulator<KrakenEpRef>() {

    override fun handleContentChange(element: KrakenEpRef, range: TextRange, newContent: String): KrakenEpRef {
        KrakenPsiUtil.replaceQuoted(element.node.findChildByType(KrakenTypes.STRING), newContent)
        return element
    }

    override fun getRangeInElement(element: KrakenEpRef): TextRange = KrakenEpRef.stringRangeInside(element) ?: TextRange(0, element.textLength)
}
