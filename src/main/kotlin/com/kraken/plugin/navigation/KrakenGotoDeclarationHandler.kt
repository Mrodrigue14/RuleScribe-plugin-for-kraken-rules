package com.kraken.plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleRef

/** Ctrl+B / Ctrl+click from a reference (an EntryPoint item) to its declarations. */
class KrakenGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        // Every visible declaration, not only the one `resolve()` picks: one name can cover
        // several @Dimension variants.
        val ruleRef = PsiTreeUtil.getParentOfType(sourceElement, KrakenRuleRef::class.java, false)
        if (ruleRef != null) {
            return KrakenPsiUtil.findRulesVisible(ruleRef, ruleRef.ruleName)
                .toTypedArray<PsiElement>()
                .takeIf { it.isNotEmpty() }
        }
        val epRef = PsiTreeUtil.getParentOfType(sourceElement, KrakenEpRef::class.java, false)
        if (epRef != null) {
            val name = epRef.entryPointName ?: return null
            return KrakenPsiUtil.findEntryPointsVisible(epRef, name)
                .toTypedArray<PsiElement>()
                .takeIf { it.isNotEmpty() }
        }

        // Declaration → usages is deliberately not handled here. Returning nothing lets the
        // platform's "Go To Declaration or Usages" show its usages popup, fed by
        // ReferencesSearch and therefore by KrakenReferencesSearcher, which applies the same
        // namespace visibility rules. The "N usages" inlay opens the same popup.
        return null
    }
}
