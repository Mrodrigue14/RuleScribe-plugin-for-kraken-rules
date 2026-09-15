package com.kraken.plugin.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.kraken.plugin.lang.KrakenFile

/**
 * Performs a move, separately from the handlers so that it can be tested without a
 * dialog.
 *
 * It works for any declaration: it only reads the element's range and file, so an
 * EntryPoint takes the same path. What a move breaks depends on the kind of declaration
 * and is decided in [KrakenMoveConflicts].
 *
 * Text is edited through the document because the plugin has no PSI element factory.
 *
 * Insert first, then delete: if the deletion failed, the declaration would exist twice
 * (which an inspection reports for rules) rather than nowhere.
 */
object KrakenDeclarationMover {

    fun move(project: Project, declaration: PsiElement, target: KrakenFile): Boolean {
        val source = declaration.containingFile as? KrakenFile ?: return false
        if (source == target) return false
        val manager = PsiDocumentManager.getInstance(project)
        val sourceDoc = manager.getDocument(source) ?: return false
        val targetDoc = manager.getDocument(target) ?: return false

        val range = declaration.textRange
        val text = sourceDoc.getText(range).trim()

        targetDoc.insertString(targetDoc.textLength, "\n\n$text\n")
        manager.commitDocument(targetDoc)
        sourceDoc.deleteString(range.startOffset, range.endOffset)
        manager.commitDocument(sourceDoc)
        return true
    }
}
