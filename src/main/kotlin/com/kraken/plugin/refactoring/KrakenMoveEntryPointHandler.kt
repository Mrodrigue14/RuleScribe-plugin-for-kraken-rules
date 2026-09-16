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
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Moves an `EntryPoint` declaration to another `.rules` file (F6).
 *
 * Same flow as [KrakenMoveRuleHandler]. The difference is direction: moving an
 * EntryPoint can break the entry points that cite it and, separately, its own items.
 * An `EntryPoint "Validation" { "My rule" }` placed where that rule is invisible becomes
 * empty without a line changing. [KrakenMoveConflicts.EntryPointMove] keeps both counts.
 */
class KrakenMoveEntryPointHandler : MoveHandlerDelegate() {

    override fun supportsLanguage(language: Language): Boolean = language == KrakenLanguage

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?): Boolean = elements.size == 1 && entryPointOf(elements[0]) != null

    override fun getActionName(elements: Array<out PsiElement>): String = "Move EntryPoint…"

    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext?,
        reference: PsiReference?,
        editor: Editor?,
    ): Boolean {
        val entryPoint = entryPointOf(element) ?: return false
        val source = entryPoint.containingFile as? KrakenFile ?: return false
        val target = chooseMoveTarget(project, source, "Move EntryPoint to File") ?: return true
        if (target == source) return true

        val broken = KrakenMoveConflicts.brokenBy(entryPoint, target)
        if (!broken.isEmpty && !confirm(project, entryPoint.name, broken)) return true

        WriteCommandAction.runWriteCommandAction(project, "Move EntryPoint", null, {
            KrakenDeclarationMover.move(project, entryPoint, target)
        })
        return true
    }

    /**
     * A `Rule` inside an EntryPoint's `Rules { }` block belongs to the rule handler;
     * otherwise both handlers would claim the caret and registration order would decide.
     */
    private fun entryPointOf(element: PsiElement): KrakenEntryPointDecl? {
        if (PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false) != null) return null
        return element as? KrakenEntryPointDecl
            ?: PsiTreeUtil.getParentOfType(element, KrakenEntryPointDecl::class.java, false)
    }

    /**
     * Both counts are announced separately because they are different decisions.
     * Answering no cancels without changing anything.
     */
    private fun confirm(
        project: Project,
        name: String?,
        broken: KrakenMoveConflicts.EntryPointMove,
    ): Boolean {
        val what = name?.let { "'$it'" } ?: "this entry point"
        val message = buildString {
            append("Moving $what there changes no text, but:\n")
            if (broken.incoming.isNotEmpty()) {
                append("\n• ${broken.incoming.size} entry point item(s) elsewhere will stop seeing it.")
            }
            if (broken.outgoing.isNotEmpty()) {
                append("\n• ${broken.outgoing.size} of its own item(s) will stop resolving from there.")
            }
            append("\n\nMove anyway?")
        }
        return Messages.showYesNoDialog(project, message, "Move EntryPoint", Messages.getWarningIcon()) ==
            Messages.YES
    }
}
