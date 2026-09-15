package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.types.KrakenTypeToken

/**
 * Validates a `Function` declaration, mirroring the engine's `FunctionValidator` and
 * `FunctionSignatureValidator`.
 *
 * The scope is deliberately syntactic. The engine validates a resolved `KrakenProject`
 * and knows every model type, so it can also report unknown types (`kvf006`, `kvf009`)
 * or a body that does not match the return type (`kvf011`). Those checks fire on an
 * absence, which is only conclusive when the type inventory is complete. The checks
 * below fire on a presence (a `|` and a `<T>` in one type, two bounds with the same
 * name, a redeclared parameter), which stays correct with partial project knowledge.
 *
 * A `Function` without a body is a `FunctionSignature` for the engine, validated with
 * other codes; see [KrakenDiagnostic].
 */
private abstract class KrakenFunctionDeclVisitor(
    private val holder: ProblemsHolder,
) : PsiElementVisitor() {

    final override fun visitElement(element: PsiElement) {
        if (element is KrakenFunctionDecl) check(element)
    }

    abstract fun check(function: KrakenFunctionDecl)
}

/** The engine code depends on whether the function has a body. */
private fun pick(signature: Boolean, forSignature: KrakenDiagnostic, forFunction: KrakenDiagnostic): KrakenDiagnostic = if (signature) forSignature else forFunction

/** Duplicates are reported from the second occurrence on: that is the one to remove. */
private fun <T> Iterable<T>.afterFirstOccurrenceOf(key: (T) -> String?): List<T> {
    val seen = mutableSetOf<String>()
    return filter { item -> key(item)?.let { !seen.add(it) } ?: false }
}

/**
 * Invalid generic bounds: two bounds for the same generic (`kvf004`/`kvf017`), or a
 * bound that is itself generic (`kvf005`/`kvf018`).
 */
class KrakenFunctionGenericBoundInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KrakenFunctionDeclVisitor(holder) {
        override fun check(function: KrakenFunctionDecl) {
            val bounds = function.genericBounds
            if (bounds.isEmpty()) return
            val signature = !function.hasBody()

            for (duplicate in bounds.afterFirstOccurrenceOf { it.generic }) {
                val diagnostic = pick(signature, KrakenDiagnostic.SIGNATURE_GENERIC_BOUND_DUPLICATE, KrakenDiagnostic.FUNCTION_GENERIC_BOUND_DUPLICATE)
                holder.registerProblem(
                    duplicate.nameElement,
                    diagnostic.format(duplicate.generic),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }

            for (bound in bounds) {
                val element = bound.boundElement ?: continue
                val text = bound.bound ?: continue
                // The engine resolves a bound without a bounds environment (single-argument
                // `resolveTypeOf`), so a bound cannot refer to another generic.
                if (KrakenTypeToken.parse(text)?.isGeneric != true) continue
                val diagnostic = pick(signature, KrakenDiagnostic.SIGNATURE_GENERIC_BOUND_IS_ITSELF_GENERIC, KrakenDiagnostic.FUNCTION_GENERIC_BOUND_IS_ITSELF_GENERIC)
                holder.registerProblem(
                    element,
                    diagnostic.format(text, bound.generic),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
    }
}

/**
 * A type mixing union and generic, such as `<T> | String`, which the engine rejects as a
 * return type (`kvf007`/`kvf020`) and as a parameter type (`kvf010`/`kvf021`).
 */
class KrakenFunctionTypeUnionGenericMixInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KrakenFunctionDeclVisitor(holder) {
        override fun check(function: KrakenFunctionDecl) {
            val signature = !function.hasBody()

            report(
                holder,
                function.returnTypeElement,
                function.returnType,
                pick(signature, KrakenDiagnostic.SIGNATURE_RETURN_TYPE_UNION_GENERIC_MIX, KrakenDiagnostic.FUNCTION_RETURN_TYPE_UNION_GENERIC_MIX),
            )
            for (parameter in function.parameterList) {
                report(
                    holder,
                    parameter.typeElement,
                    parameter.type,
                    pick(signature, KrakenDiagnostic.SIGNATURE_PARAMETER_TYPE_UNION_GENERIC_MIX, KrakenDiagnostic.FUNCTION_PARAMETER_TYPE_UNION_GENERIC_MIX),
                )
            }
        }

        private fun report(
            holder: ProblemsHolder,
            element: PsiElement?,
            text: String?,
            diagnostic: KrakenDiagnostic,
        ) {
            if (element == null || text == null) return
            val type = KrakenTypeToken.parse(text) ?: return
            if (type.isUnion && type.isGeneric) {
                holder.registerProblem(
                    element,
                    diagnostic.format(text),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
    }
}

/**
 * Two parameters with the same name (`kvf008`).
 *
 * Only for functions with a body: the engine does not name signature parameters
 * (`functionSignatureParameter : type`); RuleScribe's grammar is just more lenient.
 */
class KrakenFunctionParameterDuplicateInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KrakenFunctionDeclVisitor(holder) {
        override fun check(function: KrakenFunctionDecl) {
            if (!function.hasBody()) return
            for (duplicate in function.parameterList.afterFirstOccurrenceOf { it.name }) {
                val element = duplicate.nameElement ?: continue
                holder.registerProblem(
                    element,
                    KrakenDiagnostic.FUNCTION_PARAMETER_DUPLICATE.format(duplicate.name),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
    }
}

/**
 * A KEL-implemented function named like a native one (`kvf003`).
 *
 * This matches against the bundled catalogue, so it says nothing about functions
 * RuleScribe does not know. The engine compares names only, ignoring arity.
 *
 * Bodiless declarations are exempt: that is how the DSL declares a Java function, and
 * `FunctionSignatureValidator` does not run this check.
 */
class KrakenFunctionNativeDuplicateInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KrakenFunctionDeclVisitor(holder) {
        override fun check(function: KrakenFunctionDecl) {
            if (!function.hasBody()) return
            val name = function.name ?: return
            val anchor = function.nameIdentifier ?: return
            if (KrakenFunctionCatalog.byName(name).isEmpty()) return
            holder.registerProblem(
                anchor,
                KrakenDiagnostic.FUNCTION_NATIVE_DUPLICATE.format(name),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }
    }
}
