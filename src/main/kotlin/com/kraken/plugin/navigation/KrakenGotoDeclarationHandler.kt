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

        // 1. Référence -> déclaration(s). Toutes les déclarations visibles, pas
        //    seulement celle que `resolve()` retient : un même nom peut couvrir
        //    plusieurs variantes @Dimension, et n'en proposer qu'une mènerait à
        //    une implémentation choisie au hasard de l'ordre de l'index.
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

        // Le sens déclaration -> usages n'est délibérément PAS traité ici.
        //
        // Il l'a été jusqu'en v0.8.0 : le handler renvoyait les usages comme
        // s'ils étaient des cibles de déclaration, ce qui donnait une popup
        // plate de libellés. En ne renvoyant rien, on laisse « Go To
        // Declaration or Usages » de la plateforme prendre le relais et
        // afficher sa popup d'usages — groupée par fichier, avec l'aperçu du
        // code. Elle s'alimente de ReferencesSearch, donc de
        // KrakenReferencesSearcher, qui applique déjà les mêmes règles de
        // visibilité de namespace : la sémantique ne change pas, seule la
        // présentation s'améliore. Même popup au clic sur l'inlay « N usages »
        // (KrakenReferencesCodeVisionProvider).
        return null
    }
}
