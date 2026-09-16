package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenPsiUtil

/**
 * Reports a `@Dimension("name", ...)` whose name no `Dimension "name" : Type` declares.
 * Only fires when the project declares at least one dimension.
 */
class KrakenUndeclaredDimensionInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element.node?.elementType != KrakenTypes.DIMENSION_ANNOTATION) return
            val firstArg = element.node.findChildByType(KrakenTypes.ANNOTATION_ARG) ?: return
            val stringLeaf = firstArg.findChildByType(KrakenTypes.STRING) ?: return
            val name = KrakenPsiUtil.unquote(stringLeaf.text)
            val declared = KrakenPsiUtil.findDimensionNamesVisible(element.containingFile)
            if (declared.isNotEmpty() && name !in declared) {
                holder.registerProblem(
                    stringLeaf.psi,
                    "Dimension '$name' is not declared (expected one of: ${declared.joinToString(", ")})",
                    ProblemHighlightType.WEAK_WARNING,
                    KrakenDeclareDimensionFix(name),
                )
            }
        }
    }
}
