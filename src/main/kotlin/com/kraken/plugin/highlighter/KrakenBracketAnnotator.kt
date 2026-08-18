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
 * Colore accolades, parenthèses et crochets selon leur profondeur, et signale
 * en rouge celles qui n'ont pas de partenaire.
 *
 * L'appariement est calculé ici, à la pile. `KrakenBraceMatcher` ne peut pas
 * servir : `PairedBraceMatcher` n'expose que `getPairs`,
 * `isPairedBracesAllowedBeforeType` et `getCodeConstructStart` — il pilote le
 * surlignage de la paire sous le curseur, mais ne répond pas à « quel est le
 * partenaire de celle-ci », et ne dit rien des orphelines.
 *
 * L'annotation porte sur le fichier entier plutôt que sur chaque accolade :
 * la profondeur est une propriété de l'arbre, pas du token, et recalculer la
 * pile à chaque accolade la rendrait quadratique.
 *
 * `?[` ouvre un crochet au même titre que `[` — sans quoi le `]` de `a?[x]`
 * passerait pour orphelin.
 */
class KrakenBracketAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KrakenFile) return

        // La plateforme laisse « Rainbow » à null tant que l'utilisateur n'a
        // rien choisi, et le traite alors comme désactivé. Ici la profondeur
        // est la fonctionnalité, pas une option : non réglé vaut activé, un
        // choix explicite reste respecté.
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
                // Fermante sans ouvrante correspondante : soit il n'y en a
                // aucune, soit une paire intermédiaire est déséquilibrée, ce
                // qui rend l'appariement ambigu. Les deux cas se signalent.
                unmatched += leaf
            }
        }
        // Ce qui reste ouvert n'a jamais été refermé.
        unmatched += stack.map { it.second }

        if (depthEnabled) {
            for ((brace, depth) in depths) {
                paint(holder, brace, KrakenSyntaxHighlighter.BRACKET_DEPTH[depth % DEPTH_COUNT])
            }
        }
        // Le rouge reste posé même quand la coloration par profondeur est
        // désactivée : c'est un signalement d'erreur, pas une décoration.
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
