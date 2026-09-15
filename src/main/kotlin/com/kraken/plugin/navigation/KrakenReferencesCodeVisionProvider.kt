package com.kraken.plugin.navigation

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Clickable "N usages" inlay above declarations.
 *
 * [ReferencesCodeVisionProvider] already provides the click (the standard usages
 * popup), the label and the settings group, so only the count is left.
 *
 * The count goes through [KrakenPsiUtil] and its cached visibility model: a reference
 * whose namespace cannot see the declaration is not a usage, exactly as in Find Usages
 * and the unused rule inspection.
 */
class KrakenReferencesCodeVisionProvider : ReferencesCodeVisionProvider() {

    override fun acceptsFile(file: PsiFile): Boolean = file is KrakenFile

    override fun acceptsElement(element: PsiElement): Boolean = element is KrakenRuleDecl || element is KrakenEntryPointDecl || element is KrakenFunctionDecl

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        val usages = when (element) {
            is KrakenRuleDecl -> KrakenPsiUtil.findRuleRefsVisibleTo(element).size
            is KrakenEntryPointDecl -> KrakenPsiUtil.findEpRefsVisibleTo(element).size
            is KrakenFunctionDecl -> KrakenPsiUtil.findFunctionCallsVisibleTo(element).size
            else -> return null
        }
        return when (usages) {
            0 -> "no usages"
            1 -> "1 usage"
            else -> "$usages usages"
        }
    }

    override val id: String = ID

    /** The plugin's only inlay, so there is no relative order to set. */
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

    companion object {
        const val ID: String = "kraken.references"
    }
}
