package com.kraken.plugin.refactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenRuleDecl

class KrakenMoveRuleHandler : KrakenMoveDeclarationHandler<KrakenRuleDecl>("Rule") {

    override fun declarationOf(element: PsiElement): KrakenRuleDecl? = element as? KrakenRuleDecl ?: PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false)

    /** The count is enough, since the decision is whether to accept breaking those references. */
    override fun acceptsBreakage(project: Project, declaration: KrakenRuleDecl, target: KrakenFile): Boolean {
        val broken = KrakenMoveConflicts.brokenBy(declaration, target)
        if (broken.isEmpty()) return true
        val what = declaration.name?.let { "'$it'" } ?: "this rule"
        val message = "Moving $what there puts it in a namespace that ${broken.size} " +
            "reference(s) cannot see. Their text will not change, but they will " +
            "stop resolving.\n\nMove anyway?"
        return Messages.showYesNoDialog(project, message, commandName, Messages.getWarningIcon()) == Messages.YES
    }
}
