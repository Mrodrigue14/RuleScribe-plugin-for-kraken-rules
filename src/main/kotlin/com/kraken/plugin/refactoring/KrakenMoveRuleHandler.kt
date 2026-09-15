package com.kraken.plugin.refactoring

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenLanguage
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Moves a `Rule` declaration to another `.rules` file (F6).
 *
 * Uses [MoveHandlerDelegate.tryToMove] rather than `doMove`: the platform has no
 * natural destination container for a rule, so this handler runs the whole flow.
 *
 * The move rewrites no reference, on purpose: references are by name (see
 * [KrakenMoveConflicts]). Their resolution changes, so the user is warned beforehand.
 */
class KrakenMoveRuleHandler : MoveHandlerDelegate() {

    override fun supportsLanguage(language: Language): Boolean = language == KrakenLanguage

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?): Boolean = elements.size == 1 && ruleOf(elements[0]) != null

    override fun getActionName(elements: Array<out PsiElement>): String = "Move Rule…"

    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext?,
        reference: PsiReference?,
        editor: Editor?,
    ): Boolean {
        val rule = ruleOf(element) ?: return false
        val source = rule.containingFile as? KrakenFile ?: return false
        val target = chooseMoveTarget(project, source, "Move Rule to File") ?: return true
        if (target == source) return true

        val broken = KrakenMoveConflicts.brokenBy(rule, target)
        if (broken.isNotEmpty() && !confirm(project, rule.name, broken.size)) return true

        // WriteCommandAction, not WriteAction: the platform refuses document changes outside a
        // command, and the command makes the move undoable.
        WriteCommandAction.runWriteCommandAction(project, "Move Rule", null, {
            KrakenDeclarationMover.move(project, rule, target)
        })
        return true
    }

    private fun ruleOf(element: PsiElement): KrakenRuleDecl? = element as? KrakenRuleDecl ?: PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false)

    /**
     * The count is enough, since the decision is whether to accept breaking those
     * references. Answering no cancels without changing anything.
     */
    private fun confirm(project: Project, name: String?, count: Int): Boolean {
        val what = name?.let { "'$it'" } ?: "this rule"
        val message = "Moving $what there puts it in a namespace that $count " +
            "reference(s) cannot see. Their text will not change, but they will " +
            "stop resolving.\n\nMove anyway?"
        return Messages.showYesNoDialog(project, message, "Move Rule", Messages.getWarningIcon()) ==
            Messages.YES
    }
}
