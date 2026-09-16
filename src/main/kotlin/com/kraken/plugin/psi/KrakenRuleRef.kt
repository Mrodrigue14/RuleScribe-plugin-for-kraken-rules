package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiReference

/** Rule reference in `EntryPoint { "ruleName", ... }`. */
class KrakenRuleRef(node: ASTNode) : ASTWrapperPsiElement(node) {

    val ruleName: String
        get() = StringUtil.unquoteString(text)

    override fun getReference(): PsiReference = KrakenRuleReference(this)

    /**
     * A rule is often referenced by several EntryPoints: without a presentation the
     * navigation popup would show identical lines.
     */
    override fun getPresentation(): ItemPresentation = KrakenPresentations.of(
        this,
        KrakenPresentations.containerText(this, "\"$ruleName\""),
        KrakenPresentations.RULE_ICON,
    )
}
