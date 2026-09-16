package com.kraken.plugin.navigation

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.kraken.plugin.psi.KrakenDeclarations
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Dedicated reference search: IntelliJ's default search goes through the word index and
 * misses multi-word names ("Policy code mandatory"), so EntryPoint items are scanned by
 * name.
 */
class KrakenReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val target = queryParameters.elementToSearch
        val scope = queryParameters.effectiveSearchScope
        when (target) {
            is KrakenRuleDecl -> {
                for (ref in KrakenDeclarations.findRuleRefsVisibleTo(target)) {
                    if (!PsiSearchScopeUtil.isInScope(scope, ref)) continue
                    consumer.process(ref.reference)
                }
            }

            is KrakenEntryPointDecl -> {
                for (ref in KrakenDeclarations.findEpRefsVisibleTo(target)) {
                    if (!PsiSearchScopeUtil.isInScope(scope, ref)) continue
                    val reference = ref.reference ?: continue
                    consumer.process(reference)
                }
            }
        }
    }
}
