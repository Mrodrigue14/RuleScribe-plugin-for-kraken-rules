package com.kraken.plugin.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Gutter icon on each rule or entry point declaration that is referenced at least once,
 * navigating to the references.
 */
class KrakenRuleUsagesLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // The marker sits on the name's STRING leaf.
        if (element.node?.elementType != KrakenTypes.STRING) return
        when (element.parent?.node?.elementType) {
            KrakenTypes.RULE_NAME -> {
                val declaration = PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java) ?: return
                val references = KrakenPsiUtil.findRuleRefsVisibleTo(declaration)
                addMarker(element, references, "Referenced by ${references.size} entry point item(s)", result)
            }

            KrakenTypes.EP_NAME -> {
                val declaration = PsiTreeUtil.getParentOfType(element, KrakenEntryPointDecl::class.java) ?: return
                val references = KrakenPsiUtil.findEpRefsVisibleTo(declaration)
                addMarker(element, references, "Included by ${references.size} entry point(s)", result)
            }
        }
    }

    // A declaration without a name has no references, so the empty list skips it.
    private fun addMarker(
        element: PsiElement,
        references: List<PsiElement>,
        tooltip: String,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (references.isEmpty()) return
        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
            .setTargets(references)
            .setTooltipText(tooltip)
        result.add(builder.createLineMarkerInfo(element))
    }
}
