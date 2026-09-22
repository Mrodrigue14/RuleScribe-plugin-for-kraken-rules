package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.psi.KrakenContexts
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenRefExpr
import com.kraken.plugin.psi.KrakenScopeResolver

/**
 * Reports a bare identifier that no scope defines.
 *
 * Uses "Reference ''{0}'' not found." from
 * `kraken.el.ast.validation.AstValidatingVisitor`, but much more cautiously: the engine
 * has types and this plugin does not. An identifier is only reported when its absence
 * is certain, which excludes:
 *
 * - chain segments (`a.b.c`): without the type of `a`, nothing can be said about `b`;
 * - rules whose `On` target does not resolve, since there is no reference context;
 * - `context`, the external context root, which the engine always puts in the global
 *   scope (`ScopeBuilder`) and the DSL never declares;
 * - filter predicates on an element of unknown type, such as
 *   `context.additional.vehicles[model = …]`: the scope is undetermined and the engine
 *   accepts anything there (`Scope.isDynamic`).
 *
 * Function call heads are not `KrakenRefExpr`s in the grammar, so they never reach it.
 *
 * This lets through errors the engine catches, but it must never underline valid code.
 */
class KrakenUnresolvedIdentifierInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element !is KrakenRefExpr) return
            val name = element.referenceName
            if (name.isEmpty() || name == EXTERNAL_CONTEXT) return

            // Outside a rule or function there is no reference scope. Naming an `On` target is not
            // enough: the context must exist, or no field is known and everything would look
            // unresolved.
            val inFunction =
                PsiTreeUtil.getParentOfType(element, KrakenFunctionDecl::class.java, false) != null
            if (!inFunction && !hasResolvableTarget(element)) return

            // Filter predicate on an element of unknown type: the scope is undetermined, not empty.
            // The engine accepts anything there (Scope.isDynamic), typically under the external
            // context.
            if (KrakenScopeResolver.isInUntypedFilter(element)) return

            if (element.reference?.resolve() != null) return
            holder.registerProblem(
                element,
                KrakenDiagnostic.REFERENCE_NOT_FOUND.format(name),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }
    }

    private fun hasResolvableTarget(element: PsiElement): Boolean {
        val target = KrakenScopeResolver.targetContextName(element) ?: return false
        return KrakenContexts.contextExists(element.containingFile, target)
    }

    private companion object {
        /**
         * `ScopeBuilder` always puts `context` in the global scope, whether or not an
         * `ExternalContext` is declared.
         */
        const val EXTERNAL_CONTEXT = "context"
    }
}
