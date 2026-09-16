package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenPsiUtil

/**
 * Reports an `On Context.field` clause whose context is not declared in any visible
 * file. Only fires when at least one context is declared, to stay quiet in projects
 * without context definitions.
 */
class KrakenUnknownContextInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element.node?.elementType != KrakenTypes.RULE_TARGET) return
            val nameLeaf = contextLeaf(element) ?: return
            val known = KrakenPsiUtil.findContextNamesVisible(element.containingFile)
            if (known.isNotEmpty() && nameLeaf.text !in known) {
                holder.registerProblem(
                    nameLeaf,
                    KrakenDiagnostic.RULE_TARGET_CONTEXT_UNKNOWN.format(nameLeaf.text),
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                )
            }
        }
    }

    private fun contextLeaf(ruleTarget: PsiElement): PsiElement? {
        var child = ruleTarget.firstChild
        var seenOn = false
        while (child != null) {
            if (child.node?.elementType == KrakenTypes.ON_KW) {
                seenOn = true
            } else if (seenOn &&
                child !is PsiWhiteSpace &&
                child.node?.elementType in KrakenPsiUtil.ID_TOKENS
            ) {
                return child
            }
            child = child.nextSibling
        }
        return null
    }
}
