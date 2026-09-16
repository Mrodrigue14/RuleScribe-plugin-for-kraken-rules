package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.psi.KrakenContexts
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Reports an `On Context.field` clause whose context is not declared in any visible
 * file. Only fires when at least one context is declared, to stay quiet in projects
 * without context definitions.
 */
class KrakenUnknownContextInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        private val known by lazy { KrakenContexts.findContextNamesVisible(holder.file).toSet() }

        override fun visitElement(element: PsiElement) {
            if (element !is KrakenRuleDecl) return
            val nameLeaf = element.targetContextLeaf() ?: return
            if (known.isNotEmpty() && nameLeaf.text !in known) {
                holder.registerProblem(
                    nameLeaf,
                    KrakenDiagnostic.RULE_TARGET_CONTEXT_UNKNOWN.format(nameLeaf.text),
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                )
            }
        }
    }
}
