package com.kraken.plugin.highlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenPsiUtil

/**
 * Colore le nom appelé dans une expression KEL.
 *
 * Un highlighter lexical ne peut pas faire ce travail : `Round` et une simple
 * variable sont tous deux des `IDENTIFIER`, et seule la présence de la liste
 * d'arguments derrière le nom les sépare — une information d'arbre, pas de
 * flux de tokens. D'où cet [Annotator].
 *
 * Deux teintes distinctes, parce que la confusion utile n'est pas
 * « fonction ou variable » mais « fonction du moteur ou fonction du projet » :
 * une faute de frappe dans le nom d'une native se voit alors immédiatement, la
 * couleur retombant sur celle des fonctions déclarées.
 */
class KrakenFunctionAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KrakenFunctionCall) return
        val name = element.functionName
        if (name.isEmpty()) return

        val arity = element.argumentCount
        val attribute = when {
            KrakenFunctionCatalog.find(name, arity) != null ->
                KrakenSyntaxHighlighter.NATIVE_FUNCTION
            KrakenPsiUtil.findFunctionVisible(element, name, arity) != null ->
                KrakenSyntaxHighlighter.DECLARED_FUNCTION
            // Appel non résolu : on ne colore pas. L'inspection dédiée le
            // signale, et teindre un nom inconnu en « fonction » serait mentir.
            else -> return
        }

        val start = element.textRange.startOffset
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(start, start + name.length))
            .textAttributes(attribute)
            .create()
    }
}
