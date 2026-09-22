package com.kraken.plugin.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenDeclaration
import com.kraken.plugin.psi.KrakenRuleDecl

class KrakenMoveRuleHandler : KrakenMoveDeclarationHandler<KrakenRuleDecl>(KrakenDeclaration.Kind.RULE) {

    override fun declarationOf(element: PsiElement): KrakenRuleDecl? = PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false)

    /** The count is enough, since the decision is whether to accept breaking those references. */
    override fun breakage(declaration: KrakenRuleDecl, target: KrakenFile): String? {
        val broken = KrakenMoveConflicts.brokenBy(declaration, target)
        if (broken.isEmpty()) return null
        return "Moving ${describe(declaration)} there puts it in a namespace that ${broken.size} " +
            "reference(s) cannot see. Their text will not change, but they will stop resolving."
    }
}
