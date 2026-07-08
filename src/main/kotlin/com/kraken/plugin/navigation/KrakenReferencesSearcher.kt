package com.kraken.plugin.navigation

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Recherche de références dédiée : la recherche standard d'IntelliJ passe par
 * l'index de mots et rate les noms multi-mots ("Policy code mandatory").
 * Ici on scanne directement les items d'EntryPoint par nom.
 */
class KrakenReferencesSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val target = queryParameters.elementToSearch
        val scope = queryParameters.effectiveSearchScope
        when (target) {
            is KrakenRuleDecl -> {
                for (ref in KrakenPsiUtil.findRuleRefsVisibleTo(target)) {
                    if (!PsiSearchScopeUtil.isInScope(scope, ref)) continue
                    consumer.process(ref.reference)
                }
            }
            is KrakenEntryPointDecl -> {
                for (ref in KrakenPsiUtil.findEpRefsVisibleTo(target)) {
                    if (!PsiSearchScopeUtil.isInScope(scope, ref)) continue
                    val reference = ref.reference ?: continue
                    consumer.process(reference)
                }
            }
        }
    }
}
