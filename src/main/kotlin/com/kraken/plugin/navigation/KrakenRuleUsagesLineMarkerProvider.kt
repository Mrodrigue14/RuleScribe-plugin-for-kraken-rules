package com.kraken.plugin.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Icône de gouttière sur chaque déclaration de règle référencée par au moins
 * un item d'EntryPoint, avec navigation vers ces références.
 */
class KrakenRuleUsagesLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        // Le marqueur est posé sur la feuille STRING du nom (règle ou entry point)
        if (element.node?.elementType != KrakenTypes.STRING) return
        when (element.parent?.node?.elementType) {
            KrakenTypes.RULE_NAME -> {
                val declaration = PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java) ?: return
                if (declaration.name == null) return
                val references = KrakenPsiUtil.findRuleRefsVisibleTo(declaration)
                if (references.isEmpty()) return
                val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
                    .setTargets(references)
                    .setTooltipText("Referenced by ${references.size} entry point item(s)")
                result.add(builder.createLineMarkerInfo(element))
            }
            KrakenTypes.EP_NAME -> {
                val declaration = PsiTreeUtil.getParentOfType(element, KrakenEntryPointDecl::class.java) ?: return
                if (declaration.name == null) return
                val references = KrakenPsiUtil.findEpRefsVisibleTo(declaration)
                if (references.isEmpty()) return
                val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
                    .setTargets(references)
                    .setTooltipText("Included by ${references.size} entry point(s)")
                result.add(builder.createLineMarkerInfo(element))
            }
        }
    }
}
