package com.kraken.plugin.refactoring

import com.intellij.ide.util.TreeFileChooserFactory
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
import com.kraken.plugin.lang.KrakenFileType
import com.kraken.plugin.lang.KrakenLanguage
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Déplace une déclaration `EntryPoint` vers un autre fichier `.rules` (F6).
 *
 * Même flux que [KrakenMoveRuleHandler], et pour les mêmes raisons : les
 * références sont par nom, aucun texte ne change, seule la résolution bouge.
 *
 * Ce qui n'est pas pareil, c'est le nombre de directions. Une règle est
 * référencée ; un EntryPoint est référencé **et** référence. Le déplacer peut
 * donc casser les entry points qui le citent et, séparément, ses propres items
 * — un `EntryPoint "Validation" { "Ma règle" }` posé dans un namespace qui ne
 * voit pas cette règle devient un entry point vide sans qu'une ligne bouge à
 * l'intérieur. [KrakenMoveConflicts.EntryPointMove] tient les deux comptes
 * séparés parce que ce ne sont pas la même décision.
 */
class KrakenMoveEntryPointHandler : MoveHandlerDelegate() {

    override fun supportsLanguage(language: Language): Boolean = language == KrakenLanguage

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?): Boolean =
        elements.size == 1 && entryPointOf(elements[0]) != null

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
        val target = chooseTarget(project, source) ?: return true
        if (target == source) return true

        val broken = KrakenMoveConflicts.brokenBy(entryPoint, target)
        if (!broken.isEmpty && !confirm(project, entryPoint.name, broken)) return true

        WriteCommandAction.runWriteCommandAction(project, "Move EntryPoint", null, {
            KrakenDeclarationMover.move(project, entryPoint, target)
        })
        return true
    }

    /**
     * Une `Rule` à l'intérieur d'un bloc `Rules { }` d'EntryPoint appartient au
     * handler de règles : sans cette réserve, les deux se déclareraient
     * compétents sur le même caret et lequel gagne dépendrait de l'ordre
     * d'enregistrement.
     */
    private fun entryPointOf(element: PsiElement): KrakenEntryPointDecl? {
        if (PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false) != null) return null
        return element as? KrakenEntryPointDecl
            ?: PsiTreeUtil.getParentOfType(element, KrakenEntryPointDecl::class.java, false)
    }

    private fun chooseTarget(project: Project, source: KrakenFile): KrakenFile? {
        val chooser = TreeFileChooserFactory.getInstance(project).createFileChooser(
            "Move EntryPoint to File",
            source,
            KrakenFileType,
        ) { it is KrakenFile && it != source }
        chooser.showDialog()
        return chooser.selectedFile as? KrakenFile
    }

    /**
     * Les deux comptes sont annoncés séparément : accepter de casser des
     * références chez les autres et accepter de vider l'entry point qu'on
     * déplace ne s'arbitrent pas de la même façon. Répondre non annule sans
     * rien modifier.
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
