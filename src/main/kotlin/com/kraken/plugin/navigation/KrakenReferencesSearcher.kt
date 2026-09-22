package com.kraken.plugin.navigation

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenReferencedDeclaration

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
        val target = queryParameters.elementToSearch as? KrakenReferencedDeclaration ?: return
        // A function name is a single identifier, which the default word-index search finds.
        if (target is KrakenFunctionDecl) return
        val scope = queryParameters.effectiveSearchScope
        for (usage in target.visibleUsages()) {
            if (!PsiSearchScopeUtil.isInScope(scope, usage)) continue
            usage.reference?.let { consumer.process(it) }
        }
    }
}
