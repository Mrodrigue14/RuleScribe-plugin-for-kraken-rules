package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenRuleRef

/**
 * Signale les références de règles introuvables dans les `EntryPoint`.
 */
class KrakenUnresolvedRuleRefInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KrakenRuleRef && element.reference.resolve() == null) {
                    holder.registerProblem(
                        element,
                        "Unknown rule '${element.ruleName}'",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
                if (element is KrakenEpRef && element.reference?.resolve() == null) {
                    holder.registerProblem(
                        element,
                        "Unknown entry point '${element.entryPointName}'",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
            }
        }
}
