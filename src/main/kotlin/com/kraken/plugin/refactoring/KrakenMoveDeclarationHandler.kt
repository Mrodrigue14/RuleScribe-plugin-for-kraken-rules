package com.kraken.plugin.refactoring

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenLanguage
import com.kraken.plugin.psi.KrakenDeclaration
import com.kraken.plugin.psi.KrakenReferencedDeclaration

/**
 * Moves a top-level declaration to another `.rules` file (F6): choose the file, warn about
 * what the move breaks, then move.
 *
 * Uses [MoveHandlerDelegate.tryToMove] rather than `doMove`: the platform has no natural
 * destination container for a declaration, so the handler runs the whole flow.
 *
 * The move rewrites no reference, on purpose: references are by name (see
 * [KrakenMoveConflicts]). Their resolution changes, so the user is warned beforehand.
 */
abstract class KrakenMoveDeclarationHandler<T : KrakenReferencedDeclaration>(private val kind: KrakenDeclaration.Kind) : MoveHandlerDelegate() {

    private val commandName: String get() = "Move ${kind.label}"

    /** The declaration to move when the caret is on [element], or null if this handler does not apply. */
    protected abstract fun declarationOf(element: PsiElement): T?

    /** What moving [declaration] to [target] breaks, as a sentence starting with "Moving …", or null when it breaks nothing. */
    protected abstract fun breakage(declaration: T, target: KrakenFile): String?

    /** `'Policy code'`, or `this rule` when the declaration has no name. */
    protected fun describe(declaration: T): String = declaration.name?.let { "'$it'" } ?: "this ${kind.typeText}"

    override fun supportsLanguage(language: Language): Boolean = language == KrakenLanguage

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?): Boolean = elements.size == 1 && declarationOf(elements[0]) != null

    override fun getActionName(elements: Array<out PsiElement>): String = "$commandName…"

    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext?,
        reference: PsiReference?,
        editor: Editor?,
    ): Boolean {
        val declaration = declarationOf(element) ?: return false
        val source = declaration.containingFile as? KrakenFile ?: return false
        val target = chooseMoveTarget(project, source, "$commandName to File") ?: return true
        if (!confirm(project, breakage(declaration, target))) return true

        // WriteCommandAction, not WriteAction: the platform refuses document changes outside a
        // command, and the command makes the move undoable.
        WriteCommandAction.runWriteCommandAction(project, commandName, null, {
            KrakenDeclarationMover.move(project, declaration, target)
        })
        return true
    }

    /** Answering no cancels the move without changing anything. */
    private fun confirm(project: Project, breakage: String?): Boolean = breakage == null ||
        Messages.showYesNoDialog(project, "$breakage\n\nMove anyway?", commandName, Messages.getWarningIcon()) == Messages.YES
}
