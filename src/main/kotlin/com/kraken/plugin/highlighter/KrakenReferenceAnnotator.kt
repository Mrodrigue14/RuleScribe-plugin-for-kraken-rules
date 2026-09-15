package com.kraken.plugin.highlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenRefExpr
import com.kraken.plugin.psi.KrakenScopeResolver

/**
 * Visually separates a context name from a field or variable in a rule body.
 *
 * The lexical highlighter cannot decide: `Coverage` in `Coverage.limitAmount` and a
 * bare `limitAmount` are both `IDENTIFIER`. Scope resolution tells them apart.
 *
 * Only resolved names are coloured. An unknown name keeps the plain identifier colour,
 * because the field colour would claim it is a field. The unresolved-identifier
 * inspection stays silent in several cases (chain segments, unresolved `On` target,
 * dynamic filter predicates), so this colouring gives feedback where it says nothing.
 */
class KrakenReferenceAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KrakenRefExpr) return
        val name = element.referenceName
        if (name.isEmpty()) return

        val target = KrakenScopeResolver.resolve(element, name) ?: return
        val attribute = if (target.node?.elementType == KrakenTypes.CONTEXT_DECL) {
            KrakenSyntaxHighlighter.CONTEXT_REFERENCE
        } else {
            KrakenSyntaxHighlighter.FIELD_REFERENCE
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element.textRange)
            .textAttributes(attribute)
            .create()
    }
}
