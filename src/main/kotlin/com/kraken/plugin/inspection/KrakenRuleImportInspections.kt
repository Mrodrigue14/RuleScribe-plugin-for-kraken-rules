package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenDeclarations
import com.kraken.plugin.psi.KrakenNamespaces

/**
 * Base of the `Import Rule "X" From Ns` inspections, which mirror the engine's four
 * checks (`ResourceKrakenProjectBuilder.validateRuleImports`): unknown source
 * namespace, rule missing from it, clash with a local rule, ambiguous import.
 */
private abstract class KrakenRuleImportVisitorBase : PsiElementVisitor() {

    final override fun visitElement(element: PsiElement) {
        if (element.node?.elementType != KrakenTypes.RULE_IMPORT_DECL) return
        val imports = KrakenNamespaces.ruleImportsIn(element)
        if (imports.isNotEmpty()) checkImportDecl(element, imports)
    }

    abstract fun checkImportDecl(
        decl: PsiElement,
        imports: List<KrakenNamespaces.RuleImport>,
    )
}

/**
 * Namespace of the current file: engine messages always name the import's target
 * namespace ("… to ''{2}''").
 */
private fun targetNamespaceOf(decl: PsiElement): String = (decl.containingFile as? KrakenFile)?.let { KrakenNamespaces.namespaceOf(it) }.orEmpty()

/** The namespace named after `From` does not exist in any project file. */
class KrakenImportUnknownNamespaceInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KrakenRuleImportVisitorBase() {
        override fun checkImportDecl(
            decl: PsiElement,
            imports: List<KrakenNamespaces.RuleImport>,
        ) {
            val first = imports.first()
            if (!KrakenNamespaces.namespaceExists(decl.project, first.sourceNamespace)) {
                holder.registerProblem(
                    first.namespaceElement,
                    KrakenDiagnostic.IMPORT_UNKNOWN_NAMESPACE.format(
                        first.ruleName,
                        first.sourceNamespace,
                        targetNamespaceOf(decl),
                    ),
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                )
            }
        }
    }
}

/** The imported rule does not exist in the source namespace. */
class KrakenImportUnknownRuleInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KrakenRuleImportVisitorBase() {
        override fun checkImportDecl(
            decl: PsiElement,
            imports: List<KrakenNamespaces.RuleImport>,
        ) {
            // Like the engine, only check the rule when the source namespace exists; otherwise the
            // unknown namespace inspection covers it.
            for (import in imports) {
                if (!KrakenNamespaces.namespaceExists(decl.project, import.sourceNamespace)) continue
                val found = KrakenDeclarations.findRuleInNamespace(
                    decl.project,
                    import.sourceNamespace,
                    import.ruleName,
                )
                if (found == null) {
                    holder.registerProblem(
                        import.nameElement,
                        KrakenDiagnostic.IMPORT_UNKNOWN_RULE.format(
                            import.ruleName,
                            import.sourceNamespace,
                            targetNamespaceOf(decl),
                        ),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                    )
                }
            }
        }
    }
}

/** The imported name clashes with a locally declared rule. */
class KrakenImportNameClashInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KrakenRuleImportVisitorBase() {
        override fun checkImportDecl(
            decl: PsiElement,
            imports: List<KrakenNamespaces.RuleImport>,
        ) {
            val file = decl.containingFile as? KrakenFile ?: return
            val localNs = KrakenNamespaces.namespaceOf(file)
            for (import in imports) {
                val local = KrakenDeclarations.findRuleInNamespace(
                    decl.project,
                    localNs,
                    import.ruleName,
                )
                if (local != null) {
                    holder.registerProblem(
                        import.nameElement,
                        KrakenDiagnostic.IMPORT_DUPLICATE.format(
                            import.ruleName,
                            import.sourceNamespace,
                            localNs,
                        ),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    )
                }
            }
        }
    }
}

/** The same rule name is imported more than once, from the same or different namespaces. */
class KrakenImportAmbiguousInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KrakenRuleImportVisitorBase() {
        // The engine groups a namespace's imports by rule name and rejects any name imported
        // more than once.
        private val allImports by lazy { KrakenNamespaces.ruleImportsForNamespaceOf(holder.file) }

        override fun checkImportDecl(
            decl: PsiElement,
            imports: List<KrakenNamespaces.RuleImport>,
        ) {
            for (import in imports) {
                val sameName = allImports.filter { it.ruleName == import.ruleName }
                if (sameName.size > 1) {
                    val sources = sameName.map { it.sourceNamespace }.distinct()
                        .joinToString(", ")
                    holder.registerProblem(
                        import.nameElement,
                        KrakenDiagnostic.IMPORT_AMBIGUOUS.format(
                            import.ruleName,
                            targetNamespaceOf(decl),
                            sources,
                        ),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    )
                }
            }
        }
    }
}
