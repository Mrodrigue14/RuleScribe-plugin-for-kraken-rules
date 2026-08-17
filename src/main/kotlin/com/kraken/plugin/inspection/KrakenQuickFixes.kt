package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Correctifs proposés depuis les inspections.
 *
 * Ils écrivent dans le document plutôt que de construire du PSI : le plugin
 * n'a pas de fabrique d'éléments, et en créer une pour insérer deux lignes
 * coûterait plus que ça ne rapporte. C'est déjà l'approche de
 * [KrakenAddOnClauseIntention].
 *
 * Chaque correctif laisse une valeur à remplir plutôt que d'en inventer une :
 * ni le type d'une dimension ni la valeur qui distingue deux règles ne se
 * déduisent du fichier, et deviner produirait du code faux d'aspect correct.
 */

/** Déclare la dimension qu'une annotation `@Dimension` référence sans qu'elle existe. */
internal class KrakenDeclareDimensionFix(private val dimensionName: String) : LocalQuickFix {

    override fun getFamilyName(): String = "Declare dimension"

    override fun getName(): String = "Declare dimension '$dimensionName'"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile ?: return
        val manager = PsiDocumentManager.getInstance(project)
        val document = manager.getDocument(file) ?: return

        val offset = declarationOffset(file)
        val text = buildString {
            if (offset > 0) append('\n')
            append("Dimension \"").append(dimensionName).append("\" : String\n")
        }
        document.insertString(offset, text)
        manager.commitDocument(document)
    }

    /**
     * Après la dernière dimension déclarée, pour les garder groupées ; sinon
     * après l'en-tête, puisque `kraken_file ::= namespace_decl? import_decl*
     * model_item*` impose qu'une déclaration de dimension le suive.
     */
    private fun declarationOffset(file: PsiFile): Int {
        val anchors = file.node.getChildren(null)
            .filter { it.elementType in HEADER_AND_DIMENSION }
        return anchors.lastOrNull()?.textRange?.endOffset ?: 0
    }

    private companion object {
        val HEADER_AND_DIMENSION = setOf(
            KrakenTypes.DIMENSION_DECL,
            KrakenTypes.NAMESPACE_DECL,
            KrakenTypes.INCLUDE_DECL,
            KrakenTypes.RULE_IMPORT_DECL,
        )
    }
}

/**
 * Ajoute une annotation `@Dimension` à une règle dupliquée.
 *
 * Dupliquer un nom de règle est légitime en Kraken quand chaque variante porte
 * une dimension différente — c'est le mécanisme de variabilité du moteur. Le
 * correctif pose donc l'annotation à remplir, il ne supprime pas la règle.
 */
internal class KrakenAddDimensionAnnotationFix : LocalQuickFix {

    override fun getFamilyName(): String = "Add a differentiating @Dimension annotation"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val rule = PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false) ?: return
        val file = rule.containingFile
        val manager = PsiDocumentManager.getInstance(project)
        val document = manager.getDocument(file) ?: return

        val offset = rule.textRange.startOffset
        val indent = indentAt(document.text, offset)
        document.insertString(offset, "@Dimension(\"dimensionName\", \"value\")\n$indent")
        manager.commitDocument(document)
    }

    /** L'annotation doit s'aligner sur la règle, qui peut être imbriquée dans un `Rules { }`. */
    private fun indentAt(text: String, offset: Int): String {
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        return text.substring(lineStart, offset).takeWhile { it == ' ' || it == '\t' }
    }
}
