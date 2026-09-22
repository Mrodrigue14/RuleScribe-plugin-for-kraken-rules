package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.psi.KrakenDeclarations
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Reports two visible rules with the same name and the same dimensions
 * (`RuleVersionDuplicationValidator`). Same-named rules are legitimate variants when
 * their `@Dimension` values differ, including values set on an enclosing `Rules` block.
 */
class KrakenDuplicateRuleInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element !is KrakenRuleDecl) return
            val name = element.name ?: return
            val dimensions = element.dimensions()
            val versions = KrakenDeclarations.findRulesVisible(element, name).count { it.dimensions() == dimensions }
            if (versions > 1) {
                holder.registerProblem(
                    element.nameIdentifier ?: element,
                    KrakenDiagnostic.DUPLICATE_RULE_VERSION.format(),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    KrakenAddDimensionAnnotationFix(),
                )
            }
        }
    }
}
