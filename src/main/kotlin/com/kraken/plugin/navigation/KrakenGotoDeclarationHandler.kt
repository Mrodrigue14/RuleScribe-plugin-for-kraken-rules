package com.kraken.plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.psi.KrakenRuleRef

/**
 * Navigation Ctrl+B / Ctrl+clic, dans les deux sens :
 * - depuis une référence (item d'EntryPoint) vers la déclaration ;
 * - depuis le nom d'une déclaration (Rule ou EntryPoint) vers les items
 *   d'EntryPoint qui la référencent (popup si plusieurs).
 */
class KrakenGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        // 1. Référence -> déclaration
        val ruleRef = PsiTreeUtil.getParentOfType(sourceElement, KrakenRuleRef::class.java, false)
        if (ruleRef != null) {
            val target = ruleRef.reference.resolve() ?: return null
            return arrayOf(target)
        }
        val epRef = PsiTreeUtil.getParentOfType(sourceElement, KrakenEpRef::class.java, false)
        if (epRef != null) {
            val target = epRef.reference?.resolve() ?: return null
            return arrayOf(target)
        }

        // 2. Déclaration -> usages (Ctrl+clic sur le nom déclaré)
        if (sourceElement.node?.elementType == KrakenTypes.STRING) {
            when (sourceElement.parent?.node?.elementType) {
                KrakenTypes.RULE_NAME -> {
                    val declaration =
                        PsiTreeUtil.getParentOfType(sourceElement, KrakenRuleDecl::class.java) ?: return null
                    if (declaration.name == null) return null
                    val references = KrakenPsiUtil.findRuleRefsVisibleTo(declaration)
                    if (references.isNotEmpty()) {
                        return references.filterIsInstance<PsiElement>().toTypedArray()
                    }
                }
                KrakenTypes.EP_NAME -> {
                    val declaration =
                        PsiTreeUtil.getParentOfType(sourceElement, KrakenEntryPointDecl::class.java) ?: return null
                    if (declaration.name == null) return null
                    val references = KrakenPsiUtil.findEpRefsVisibleTo(declaration)
                    if (references.isNotEmpty()) {
                        return references.filterIsInstance<PsiElement>().toTypedArray()
                    }
                }
            }
        }
        return null
    }
}
