package com.kraken.plugin.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.kraken.plugin.lang.KrakenFile

/**
 * Le déplacement proprement dit, séparé du handler pour être testable sans
 * dialogue.
 *
 * Une déclaration quelconque, pas seulement une `Rule` : le déplacement ne lit
 * de son argument que son étendue et son fichier, si bien qu'un EntryPoint
 * suit le même chemin. Ce qui diffère d'un cas à l'autre est ce que le
 * déplacement casse, et cela se décide dans [KrakenMoveConflicts].
 *
 * Le texte est manipulé au niveau du document : le plugin n'a pas de fabrique
 * d'éléments PSI, et en créer une pour déplacer un bloc coûterait plus que ça
 * ne rapporte — même raison que pour les correctifs d'inspection.
 *
 * Insertion **puis** suppression, dans cet ordre : si la seconde échouait, la
 * déclaration existerait en double, ce qu'une inspection signale déjà pour une
 * règle, plutôt que nulle part, ce que rien ne rattraperait.
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
