package com.kraken.plugin.inspection

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenRuleDecl

class KrakenAddOnClauseIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Add missing 'On' clause"

    override fun getText(): String = "Add missing 'On' clause"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val rule = PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false)
        return rule != null && !rule.hasTarget()
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (editor == null) return
        val rule = PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false) ?: return

        // Insert after the rule name when there is one, otherwise after the Rule keyword.
        val anchor = rule.node.findChildByType(KrakenTypes.RULE_NAME)
            ?: rule.node.findChildByType(KrakenTypes.RULE_KW)
            ?: return
        val offset = anchor.textRange.endOffset
        val placeholder = " On ContextName.field"

        editor.document.insertString(offset, placeholder)

        // Select the placeholder so the user can replace it.
        val start = offset + " On ".length
        editor.caretModel.moveToOffset(start)
        editor.selectionModel.setSelection(start, offset + placeholder.length)
    }
}
