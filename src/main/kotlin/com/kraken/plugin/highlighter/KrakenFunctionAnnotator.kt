package com.kraken.plugin.highlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionTarget

/**
 * Colours the called name in a KEL expression.
 *
 * The lexical highlighter cannot do it: `Round` and a plain variable are both
 * `IDENTIFIER`, and only the argument list after the name tells them apart, which is
 * tree information.
 *
 * Native and project functions get different colours, so a typo in a native name
 * shows up at once: its colour falls back to that of declared functions.
 */
class KrakenFunctionAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KrakenFunctionCall) return
        val name = element.functionName
        if (name.isEmpty()) return

        val attribute = when (element.target()) {
            is KrakenFunctionTarget.Native -> KrakenSyntaxHighlighter.NATIVE_FUNCTION

            is KrakenFunctionTarget.Declared -> KrakenSyntaxHighlighter.DECLARED_FUNCTION

            // Unresolved call: left uncoloured, since colouring an unknown name as a function
            // would be wrong.
            null -> return
        }

        val start = element.textRange.startOffset
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(start, start + name.length))
            .textAttributes(attribute)
            .create()
    }
}
