package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.impl.source.tree.LeafElement
import com.kraken.plugin.parser.KrakenTypes

/**
 * Déclaration `EntryPoint "nom" { ... }`.
 */
class KrakenEntryPointDecl(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? = nameLeaf()?.psi

    override fun getName(): String? = nameLeaf()?.text?.let(KrakenPsiUtil::unquote)

    /** Cible de la navigation référence -> declaration (voir KrakenRuleDecl). */
    override fun getPresentation(): ItemPresentation = KrakenPresentations.of(
        this,
        KrakenPresentations.declarationText(this, name, "EntryPoint"),
        KrakenPresentations.ENTRY_POINT_ICON,
    )

    override fun setName(name: String): PsiElement {
        val leaf = nameLeaf()
        if (leaf is LeafElement) {
            val quote = leaf.text.firstOrNull() ?: '"'
            leaf.replaceWithText("$quote$name$quote")
        }
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    private fun nameLeaf(): ASTNode? =
        node.findChildByType(KrakenTypes.EP_NAME)?.findChildByType(KrakenTypes.STRING)
}
