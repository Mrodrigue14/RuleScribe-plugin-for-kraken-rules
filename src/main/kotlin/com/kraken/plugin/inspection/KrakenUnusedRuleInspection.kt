package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Signale une règle qui n'est référencée par aucun `EntryPoint` du projet :
 * elle ne sera jamais évaluée par le moteur (code mort probable).
 */
class KrakenUnusedRuleInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is KrakenRuleDecl) return
                val name = element.name ?: return
                if (KrakenPsiUtil.findRuleRefsVisibleTo(element).isEmpty()) {
                    holder.registerProblem(
                        element.nameIdentifier ?: element,
                        "Rule '$name' is not referenced by any entry point that can see its namespace",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL
                    )
                }
            }
        }
}
