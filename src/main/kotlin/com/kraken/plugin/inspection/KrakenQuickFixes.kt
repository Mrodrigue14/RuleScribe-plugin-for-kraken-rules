package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenRuleDecl

/*
 * Quick fixes offered by inspections.
 *
 * They edit the document instead of building PSI: the plugin has no element factory,
 * and writing one to insert two lines would cost more than it saves.
 * [KrakenAddOnClauseIntention] does the same.
 *
 * Each fix leaves a value to fill in rather than inventing one: neither a dimension's
 * type nor the value separating two rules can be derived from the file.
 */

/** Declares the dimension a `@Dimension` annotation references but that does not exist. */
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
     * After the last declared dimension, to keep them grouped; otherwise after the header,
     * since `kraken_file ::= namespace_decl? import_decl* model_item*` requires a dimension
     * declaration to follow it.
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
 * Adds a `@Dimension` annotation to a duplicate rule.
 *
 * Duplicate rule names are legitimate when each variant has a different dimension (the
 * engine's variability mechanism), so the fix adds the annotation to fill in instead of
 * deleting the rule.
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
        // The annotation must align with the rule, which may be nested in `Rules { }`.
        val indent = DocumentUtil.getIndent(document, offset)
        document.insertString(offset, "@Dimension(\"dimensionName\", \"value\")\n$indent")
        manager.commitDocument(document)
    }
}
