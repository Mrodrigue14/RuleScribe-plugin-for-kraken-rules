package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRefExpr
import com.kraken.plugin.psi.KrakenScopeResolver

/**
 * Signale un identifiant nu qu'aucune portée ne définit.
 *
 * Reprend « Reference ''{0}'' not found. » de
 * `kraken.el.ast.validation.AstValidatingVisitor`, mais **beaucoup plus
 * prudemment** : le moteur dispose des types, pas nous. Un identifiant n'est
 * donc signalé que lorsque l'absence est certaine, ce qui exclut :
 *
 * - les **segments de chaîne** (`a.b.c`) : sans le type de `a`, on ne peut rien
 *   affirmer sur `b` ;
 * - les règles dont la cible `On` ne se résout pas — sans contexte de
 *   référence, tout identifiant serait suspect ;
 * - `context`, la racine du contexte externe, que le moteur place d'office
 *   dans la portée globale (`ScopeBuilder`) et qui n'est déclarée nulle part
 *   dans le DSL ;
 * - les prédicats de filtre dont le type de l'élément est inconnu, par exemple
 *   `context.additional.vehicles[model = …]` : la portée y est indéterminée,
 *   et le moteur lui-même y accepte tout (`Scope.isDynamic`) ;
 * - les têtes d'appel de fonction — aucune inspection ne les valide (voir
 *   ROADMAP.md : nécessiterait un projet propriétaire pour être vérifié
 *   fiablement).
 *
 * Le compromis est assumé : cette inspection laisse passer des erreurs que le
 * moteur attrape. Elle ne doit pas, en revanche, souligner du code valide.
 */
class KrakenUnresolvedIdentifierInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is KrakenRefExpr) return
                val name = element.referenceName
                if (name.isEmpty() || name == EXTERNAL_CONTEXT) return
                if (isCallHead(element)) return

                // Hors d'une règle ou d'une fonction, aucune portée de
                // référence : rien à affirmer. Il ne suffit pas que la clause
                // `On` nomme une cible, encore faut-il que ce contexte existe —
                // sinon aucun champ n'est connu et tout paraîtrait introuvable.
                val inFunction =
                    PsiTreeUtil.getParentOfType(element, KrakenFunctionDecl::class.java, false) != null
                if (!inFunction && !hasResolvableTarget(element)) return

                // Prédicat de filtre dont on ignore le type de l'élément :
                // portée indéterminée, pas vide. Le moteur y accepte tout
                // (Scope.isDynamic), typiquement sous le contexte externe.
                if (KrakenScopeResolver.isInFilterPredicate(element) &&
                    KrakenScopeResolver.filterContext(element) == null
                ) return

                if (element.reference?.resolve() != null) return
                holder.registerProblem(
                    element,
                    KrakenDiagnostic.REFERENCE_NOT_FOUND.format(name),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        }

    private fun hasResolvableTarget(element: PsiElement): Boolean {
        val target = KrakenScopeResolver.targetContextName(element) ?: return false
        return KrakenPsiUtil.findContextDecl(element.containingFile, target) != null
    }

    /** `Round(x)` : `Round` est une tête d'appel, pas une référence à résoudre. */
    private fun isCallHead(element: KrakenRefExpr): Boolean =
        PsiTreeUtil.getParentOfType(element, KrakenFunctionCall::class.java, false)
            ?.node?.findChildByType(KrakenTypes.CALL_ARGS)
            ?.let { element.textRange.endOffset <= it.startOffset }
            ?: false

    private companion object {
        /**
         * `ScopeBuilder` met toujours `context` dans la portée globale, qu'un
         * `ExternalContext` soit déclaré ou non.
         */
        const val EXTERNAL_CONTEXT = "context"
    }
}
