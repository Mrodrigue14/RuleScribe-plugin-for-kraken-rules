package com.kraken.plugin.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Le déplacement proprement dit, séparé du handler pour être testable sans
 * dialogue.
 *
 * Le texte est manipulé au niveau du document : le plugin n'a pas de fabrique
 * d'éléments PSI, et en créer une pour déplacer un bloc coûterait plus que ça
 * ne rapporte — même raison que pour les correctifs d'inspection.
 *
 * Insertion **puis** suppression, dans cet ordre : si la seconde échouait, la
 * règle existerait en double, ce qu'une inspection signale déjà, plutôt que
 * nulle part, ce que rien ne rattraperait.
 */
object KrakenRuleMover {

    fun move(project: Project, rule: KrakenRuleDecl, target: KrakenFile): Boolean {
        val source = rule.containingFile as? KrakenFile ?: return false
        if (source == target) return false
        val manager = PsiDocumentManager.getInstance(project)
        val sourceDoc = manager.getDocument(source) ?: return false
        val targetDoc = manager.getDocument(target) ?: return false

        val range = rule.textRange
        val text = sourceDoc.getText(range).trim()

        targetDoc.insertString(targetDoc.textLength, "\n\n$text\n")
        manager.commitDocument(targetDoc)
        sourceDoc.deleteString(range.startOffset, range.endOffset)
        manager.commitDocument(sourceDoc)
        return true
    }
}
