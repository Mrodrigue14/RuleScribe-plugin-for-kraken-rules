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
 * Signale deux règles portant le même nom sans annotation `@Dimension`
 * différenciante. Dupliquer un nom de règle est légitime en Kraken quand
 * chaque variante porte une dimension différente ; sans annotation, c'est
 * presque toujours une erreur.
 */
class KrakenDuplicateRuleInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
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
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                }
            }
        }

    private fun hasAnnotations(rule: KrakenRuleDecl): Boolean =
        rule.node.findChildByType(KrakenTypes.ANNOTATION) != null
}
