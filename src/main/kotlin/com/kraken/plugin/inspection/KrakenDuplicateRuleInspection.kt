package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Reports two rules with the same name and no distinguishing `@Dimension` annotation.
 * Duplicate names are legitimate in Kraken when each variant has a different
 * dimension; without annotations it is almost always a mistake.
 */
class KrakenDuplicateRuleInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element !is KrakenRuleDecl) return
            val name = element.name ?: return
            if (hasAnnotations(element)) return
            val duplicates = KrakenPsiUtil.findRulesVisible(element)
                .filter { it.name == name && !hasAnnotations(it) }
            if (duplicates.size > 1) {
                holder.registerProblem(
                    element.nameIdentifier ?: element,
                    KrakenDiagnostic.DUPLICATE_RULE_VERSION.format(),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    KrakenAddDimensionAnnotationFix(),
                )
            }
        }
    }

    private fun hasAnnotations(rule: KrakenRuleDecl): Boolean = rule.node.findChildByType(KrakenTypes.ANNOTATION) != null
}
