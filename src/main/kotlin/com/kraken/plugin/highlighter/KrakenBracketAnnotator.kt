package com.kraken.plugin.highlighter

import com.intellij.codeHighlighting.RainbowHighlighter
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenLanguage
import com.kraken.plugin.parser.KrakenTypes

/**
 * Colours braces, parentheses and brackets by nesting depth, and marks the ones
 * without a partner in red.
 *
 * Matching is computed here with a stack. `PairedBraceMatcher`, behind
 * `KrakenBraceMatcher`, only drives the highlight of the pair under the caret: it
 * cannot say which brace closes a given one, and knows nothing about unmatched ones.
 *
 * The whole file is annotated at once because depth is a property of the tree;
 * rebuilding the stack for each brace would be quadratic.
 *
 * `?[` opens a bracket like `[`, otherwise the `]` of `a?[x]` would look unmatched.
 */
class KrakenBracketAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KrakenFile) return

        // The platform leaves "Rainbow" null until the user picks a value, and treats null
        // as disabled. Depth colouring is the feature here, so unset means enabled; an
        // explicit choice is still respected.
        val scheme = EditorColorsManager.getInstance().globalScheme
        val depthEnabled = RainbowHighlighter.isRainbowEnabled(scheme, KrakenLanguage) ?: true

        val stack = ArrayDeque<Pair<Kind, PsiElement>>()
        val unmatched = mutableListOf<PsiElement>()
        val depths = mutableListOf<Pair<PsiElement, Int>>()

        for (leaf in PsiTreeUtil.findChildrenOfType(element, PsiElement::class.java)) {
            if (leaf.firstChild != null) continue
            val type = leaf.node?.elementType ?: continue
            val opener = OPENERS[type]
            if (opener != null) {
                stack.addLast(opener to leaf)
                continue
            }
            val closer = CLOSERS[type] ?: continue
            if (stack.lastOrNull()?.first == closer) {
                val (_, open) = stack.removeLast()
                depths += open to stack.size
                depths += leaf to stack.size
            } else {
                // A closing brace with no matching opener: either none exists, or an intermediate
                // pair is unbalanced and the match is ambiguous. Both are marked.
                unmatched += leaf
            }
        }
        unmatched += stack.map { it.second }

        if (depthEnabled) {
            for ((brace, depth) in depths) {
                paint(holder, brace, KrakenSyntaxHighlighter.BRACKET_DEPTH[depth % DEPTH_COUNT])
            }
        }
        // Unmatched braces stay red even when depth colouring is off: this reports an error,
        // it is not decoration.
        for (brace in unmatched) {
            paint(holder, brace, KrakenSyntaxHighlighter.UNMATCHED_BRACKET)
        }
    }

    private fun paint(holder: AnnotationHolder, element: PsiElement, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element.textRange)
            .textAttributes(key)
            .create()
    }

    private enum class Kind { BRACE, PAREN, BRACKET }

    private companion object {
        val DEPTH_COUNT = KrakenSyntaxHighlighter.BRACKET_DEPTH.size

        val OPENERS = mapOf(
            KrakenTypes.LBRACE to Kind.BRACE,
            KrakenTypes.LPAREN to Kind.PAREN,
            KrakenTypes.LBRACKET to Kind.BRACKET,
            KrakenTypes.QLBRACKET to Kind.BRACKET,
        )

        val CLOSERS = mapOf(
            KrakenTypes.RBRACE to Kind.BRACE,
            KrakenTypes.RPAREN to Kind.PAREN,
            KrakenTypes.RBRACKET to Kind.BRACKET,
        )
    }
}
