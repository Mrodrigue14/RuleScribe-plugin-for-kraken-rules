package com.kraken.plugin.refactoring

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenLanguage

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
abstract class KrakenMoveDeclarationHandler<T : PsiElement>(private val kindName: String) : MoveHandlerDelegate() {

    protected val commandName: String get() = "Move $kindName"

    /** The declaration to move when the caret is on [element], or null if this handler does not apply. */
    protected abstract fun declarationOf(element: PsiElement): T?

    /** True when the move breaks nothing, or the user accepts what it breaks. Answering no cancels without changing anything. */
    protected abstract fun acceptsBreakage(project: Project, declaration: T, target: KrakenFile): Boolean

    override fun supportsLanguage(language: Language): Boolean = language == KrakenLanguage

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?): Boolean = elements.size == 1 && declarationOf(elements[0]) != null

    override fun getActionName(elements: Array<out PsiElement>): String = "Move $kindName…"

    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext?,
        reference: PsiReference?,
        editor: Editor?,
    ): Boolean {
        val declaration = declarationOf(element) ?: return false
        val source = declaration.containingFile as? KrakenFile ?: return false
        val target = chooseMoveTarget(project, source, "Move $kindName to File") ?: return true
        if (!acceptsBreakage(project, declaration, target)) return true

        // WriteCommandAction, not WriteAction: the platform refuses document changes outside a
        // command, and the command makes the move undoable.
        WriteCommandAction.runWriteCommandAction(project, commandName, null, {
            KrakenDeclarationMover.move(project, declaration, target)
        })
        return true
    }
}
