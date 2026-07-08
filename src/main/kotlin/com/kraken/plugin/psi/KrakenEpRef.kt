package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafElement
import com.kraken.plugin.parser.KrakenTypes

/**
 * Item `EntryPoint "nom"` à l'intérieur d'un autre EntryPoint :
 * référence navigable vers la déclaration correspondante.
 */
class KrakenEpRef(node: ASTNode) : ASTWrapperPsiElement(node) {

    val entryPointName: String?
        get() = node.findChildByType(KrakenTypes.STRING)?.text?.let(KrakenPsiUtil::unquote)

    override fun getReference(): PsiReference? {
        val range = stringRangeInside(this) ?: return null
        return KrakenEntryPointReference(this, range)
    }

    companion object {
        fun stringRangeInside(element: KrakenEpRef): TextRange? {
            val leaf = element.node.findChildByType(KrakenTypes.STRING) ?: return null
            val start = leaf.startOffset - element.node.startOffset
            return if (leaf.textLength >= 2) {
                TextRange(start + 1, start + leaf.textLength - 1)
            } else {
                TextRange(start, start + leaf.textLength)
            }
        }
    }
}

class KrakenEntryPointReference(element: KrakenEpRef, range: TextRange) :
    PsiReferenceBase<KrakenEpRef>(element, range) {

    override fun resolve(): PsiElement? {
        val name = element.entryPointName ?: return null
        return KrakenPsiUtil.findEntryPointVisible(element, name)
    }

    override fun getVariants(): Array<Any> =
        KrakenPsiUtil.findEntryPointsVisible(element)
            .mapNotNull { it.name }
            .distinct()
            .toTypedArray()
}

class KrakenEpRefManipulator : AbstractElementManipulator<KrakenEpRef>() {

    override fun handleContentChange(element: KrakenEpRef, range: TextRange, newContent: String): KrakenEpRef {
        val leaf = element.node.findChildByType(KrakenTypes.STRING)
        if (leaf is LeafElement) {
            val quote = leaf.text.firstOrNull() ?: '"'
            leaf.replaceWithText("$quote$newContent$quote")
        }
        return element
    }

    override fun getRangeInElement(element: KrakenEpRef): TextRange =
        KrakenEpRef.stringRangeInside(element) ?: TextRange(0, element.textLength)
}
