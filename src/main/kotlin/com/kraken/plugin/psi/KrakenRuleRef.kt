package com.kraken.plugin.psi

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference

/** Rule reference in `EntryPoint { "ruleName", ... }`. */
class KrakenRuleRef(node: ASTNode) : KrakenQuotedNameElement(node) {

    val ruleName: String
        get() = referencedName.orEmpty()

    override fun getReference(): PsiReference = KrakenRuleReference(this)

    /**
     * A rule is often referenced by several EntryPoints: without a presentation the
     * navigation popup would show identical lines.
     */
    override fun getPresentation(): ItemPresentation = KrakenPresentations.of(
        this,
        KrakenPresentations.containerText(this, "\"$ruleName\""),
        KrakenDeclaration.Kind.RULE.icon,
    )
}

class KrakenRuleReference(element: KrakenRuleRef) : KrakenNameReference<KrakenRuleRef>(element, KrakenPsiUtil.insideQuotes(0, element.textLength)) {

    override fun declarationsNamed(name: String): List<PsiElement> = KrakenDeclarations.findRulesVisible(element, name)

    override fun visibleDeclarations(): List<PsiNamedElement> = KrakenDeclarations.findRulesVisible(element)
}
