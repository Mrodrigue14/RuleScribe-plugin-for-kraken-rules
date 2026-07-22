package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenPsiUtil

/**
 * Inspections des `Import Rule "X" From Ns`, miroir des quatre validations du
 * moteur Kraken (ResourceKrakenProjectBuilder.validateRuleImports) :
 * namespace source inconnu, règle introuvable dans le namespace source,
 * collision avec une règle locale, import ambigu.
 */
private abstract class KrakenRuleImportVisitorBase(
    private val holder: ProblemsHolder,
) : PsiElementVisitor() {

    final override fun visitElement(element: PsiElement) {
        if (element.node?.elementType != KrakenTypes.RULE_IMPORT_DECL) return
        val imports = KrakenPsiUtil.ruleImportsIn(element)
        if (imports.isNotEmpty()) checkImportDecl(element, imports, holder)
    }

    abstract fun checkImportDecl(
        decl: PsiElement,
        imports: List<KrakenPsiUtil.RuleImport>,
        holder: ProblemsHolder,
    )
}

/** Le namespace nommé après `From` n'existe dans aucun fichier du projet. */
class KrakenImportUnknownNamespaceInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KrakenRuleImportVisitorBase(holder) {
            override fun checkImportDecl(
                decl: PsiElement,
                imports: List<KrakenPsiUtil.RuleImport>,
                holder: ProblemsHolder,
            ) {
                val first = imports.first()
                if (!KrakenPsiUtil.namespaceExists(decl.project, first.sourceNamespace)) {
                    holder.registerProblem(
                        first.namespaceElement,
                        "Unknown namespace '${first.sourceNamespace}'",
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                    )
                }
            }
        }
}

/** La règle importée n'existe pas dans le namespace source. */
class KrakenImportUnknownRuleInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KrakenRuleImportVisitorBase(holder) {
            override fun checkImportDecl(
                decl: PsiElement,
                imports: List<KrakenPsiUtil.RuleImport>,
                holder: ProblemsHolder,
            ) {
                // Comme le moteur : ne vérifie l'existence de la règle que si
                // le namespace source existe (sinon l'autre inspection suffit).
                for (import in imports) {
                    if (!KrakenPsiUtil.namespaceExists(decl.project, import.sourceNamespace)) continue
                    val found = KrakenPsiUtil.findRuleInNamespace(
                        decl.project, import.sourceNamespace, import.ruleName
                    )
                    if (found == null) {
                        holder.registerProblem(
                            import.nameElement,
                            "Rule '${import.ruleName}' does not exist in namespace " +
                                "'${import.sourceNamespace}'",
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                        )
                    }
                }
            }
        }
}

/** Le nom importé entre en collision avec une règle déclarée localement. */
class KrakenImportNameClashInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KrakenRuleImportVisitorBase(holder) {
            override fun checkImportDecl(
                decl: PsiElement,
                imports: List<KrakenPsiUtil.RuleImport>,
                holder: ProblemsHolder,
            ) {
                val file = decl.containingFile as? KrakenFile ?: return
                val localNs = KrakenPsiUtil.namespaceOf(file)
                for (import in imports) {
                    val local = KrakenPsiUtil.findRuleInNamespace(
                        decl.project, localNs, import.ruleName
                    )
                    if (local != null) {
                        holder.registerProblem(
                            import.nameElement,
                            "Imported rule '${import.ruleName}' collides with a rule " +
                                "declared in this namespace",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                    }
                }
            }
        }
}

/** Le même nom de règle est importé plusieurs fois (namespaces différents ou non). */
class KrakenImportAmbiguousInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KrakenRuleImportVisitorBase(holder) {
            override fun checkImportDecl(
                decl: PsiElement,
                imports: List<KrakenPsiUtil.RuleImport>,
                holder: ProblemsHolder,
            ) {
                // Le moteur groupe les imports de tout le namespace par nom de
                // règle et refuse tout nom importé plus d'une fois.
                val allImports = KrakenPsiUtil.ruleImportsForNamespaceOf(decl.containingFile)
                for (import in imports) {
                    val sameName = allImports.filter { it.ruleName == import.ruleName }
                    if (sameName.size > 1) {
                        val sources = sameName.map { it.sourceNamespace }.distinct()
                            .joinToString(", ")
                        holder.registerProblem(
                            import.nameElement,
                            "Rule '${import.ruleName}' is imported more than once " +
                                "(from: $sources)",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                    }
                }
            }
        }
}
