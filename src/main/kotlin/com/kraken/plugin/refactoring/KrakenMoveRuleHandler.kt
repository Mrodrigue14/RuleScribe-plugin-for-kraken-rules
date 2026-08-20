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
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Déplace une déclaration `Rule` vers un autre fichier `.rules` (Maj+F6 → F6).
 *
 * On passe par [MoveHandlerDelegate.tryToMove] plutôt que par `doMove` : la
 * plateforme n'a aucun conteneur de destination naturel à proposer pour une
 * règle, donc ce handler prend la main sur tout le flux, choisit la
 * destination et fait le déplacement.
 *
 * **Le déplacement ne réécrit aucune référence, et c'est voulu** : elles sont
 * par nom (voir [KrakenMoveConflicts]). Ce qui change est leur résolution, et
 * c'est pourquoi l'utilisateur est averti avant, pas après.
 *
 * Le texte est manipulé au niveau du document faute de fabrique d'éléments
 * PSI, et dans l'ordre insertion-puis-suppression : si la seconde opération
 * échouait, la règle existerait en double plutôt que nulle part.
 */
class KrakenMoveRuleHandler : MoveHandlerDelegate() {

    override fun supportsLanguage(language: Language): Boolean = language == KrakenLanguage

    override fun canMove(elements: Array<out PsiElement>, targetContainer: PsiElement?): Boolean =
        elements.size == 1 && ruleOf(elements[0]) != null

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
        val target = chooseTarget(project, source) ?: return true
        if (target == source) return true

        val broken = KrakenMoveConflicts.brokenBy(rule, target)
        if (broken.isNotEmpty() && !confirm(project, rule.name, broken.size)) return true

        // WriteCommandAction et pas WriteAction : modifier un document hors
        // commande est refusé par la plateforme, et c'est la commande qui
        // rend le déplacement annulable — indispensable pour un refactoring.
        WriteCommandAction.runWriteCommandAction(project, "Move Rule", null, {
            KrakenRuleMover.move(project, rule, target)
        })
        return true
    }

    private fun ruleOf(element: PsiElement): KrakenRuleDecl? =
        element as? KrakenRuleDecl ?: PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false)

    private fun chooseTarget(project: Project, source: KrakenFile): KrakenFile? {
        val chooser = TreeFileChooserFactory.getInstance(project).createFileChooser(
            "Move Rule to File",
            source,
            KrakenFileType,
            { it is KrakenFile && it != source },
        )
        chooser.showDialog()
        return chooser.selectedFile as? KrakenFile
    }

    /**
     * Le compte suffit : détailler chaque référence demanderait une vue de
     * conflits, alors que la décision tient à « est-ce que j'accepte de les
     * casser ». Répondre non annule sans rien modifier.
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
