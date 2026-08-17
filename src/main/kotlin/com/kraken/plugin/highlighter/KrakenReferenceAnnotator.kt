package com.kraken.plugin.highlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenRefExpr
import com.kraken.plugin.psi.KrakenScopeResolver

/**
 * Distingue à l'œil un nom de contexte d'un champ ou d'une variable, dans le
 * corps d'une règle.
 *
 * Le highlighter lexical ne peut pas trancher : `Coverage` dans
 * `Coverage.limitAmount` et `limitAmount` tout court sont deux `IDENTIFIER`.
 * Ce qui les sépare est le résultat de la résolution de portée, donc une
 * information d'arbre — même raisonnement que [KrakenFunctionAnnotator] pour
 * les appels.
 *
 * **On ne colore que ce qui se résout.** Un nom inconnu garde la couleur d'un
 * identifiant ordinaire : lui donner celle d'un champ affirmerait qu'il en
 * désigne un. L'inspection dédiée décide, elle, s'il faut le signaler — et
 * elle s'abstient dans plusieurs cas (segments de chaîne, cible `On` non
 * résolue, prédicats de filtre dynamiques). La coloration positive apporte
 * donc un retour là où l'inspection se tait, sans rien prétendre sur le reste.
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
