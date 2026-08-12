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
 * Signale une clause `On Contexte.champ` dont le contexte n'est déclaré
 * nulle part dans les fichiers visibles. Ne se déclenche que si au moins
 * un contexte est déclaré (pour éviter le bruit sur les projets sans
 * définitions de contextes).
 */
class KrakenUnknownContextInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.node?.elementType != KrakenTypes.RULE_TARGET) return
                val nameLeaf = contextLeaf(element) ?: return
                val known = KrakenPsiUtil.findContextNamesVisible(element.containingFile)
                if (known.isNotEmpty() && nameLeaf.text !in known) {
                    holder.registerProblem(
                        nameLeaf,
                        KrakenDiagnostic.RULE_TARGET_CONTEXT_UNKNOWN.format(nameLeaf.text),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
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
            } else if (seenOn && child !is PsiWhiteSpace &&
                child.node?.elementType in KrakenPsiUtil.ID_TOKENS
            ) {
                return child
            }
            child = child.nextSibling
        }
        return null
    }
}
