package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiReference

/**
 * Référence à une règle dans un `EntryPoint { "nomDeRegle", ... }`.
 */
class KrakenRuleRef(node: ASTNode) : ASTWrapperPsiElement(node) {

    val ruleName: String
        get() = KrakenPsiUtil.unquote(text)

    override fun getReference(): PsiReference = KrakenRuleReference(this)

    /**
     * Une règle est souvent référencée par plusieurs EntryPoints : sans
     * présentation, le popup de navigation afficherait N lignes identiques.
     */
    override fun getPresentation(): ItemPresentation = KrakenPresentations.of(
        this,
        KrakenPresentations.containerText(this, "\"$ruleName\""),
        KrakenPresentations.RULE_ICON,
    )
}
