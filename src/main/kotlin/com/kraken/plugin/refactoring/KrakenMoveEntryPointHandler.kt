package com.kraken.plugin.refactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Moves an `EntryPoint` declaration. Unlike a rule, moving an EntryPoint can break the
 * entry points that cite it and, separately, its own items: an
 * `EntryPoint "Validation" { "My rule" }` placed where that rule is invisible becomes
 * empty without a line changing. [KrakenMoveConflicts.EntryPointMove] keeps both counts.
 */
class KrakenMoveEntryPointHandler : KrakenMoveDeclarationHandler<KrakenEntryPointDecl>("EntryPoint") {

    /**
     * A `Rule` inside an EntryPoint's `Rules { }` block belongs to the rule handler;
     * otherwise both handlers would claim the caret and registration order would decide.
     */
    override fun declarationOf(element: PsiElement): KrakenEntryPointDecl? {
        if (PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false) != null) return null
        return element as? KrakenEntryPointDecl
            ?: PsiTreeUtil.getParentOfType(element, KrakenEntryPointDecl::class.java, false)
    }

    /** Both counts are announced separately because they are different decisions. */
    override fun acceptsBreakage(project: Project, declaration: KrakenEntryPointDecl, target: KrakenFile): Boolean {
        val broken = KrakenMoveConflicts.brokenBy(declaration, target)
        if (broken.isEmpty) return true
        val what = declaration.name?.let { "'$it'" } ?: "this entry point"
        val message = buildString {
            append("Moving $what there changes no text, but:\n")
            if (broken.incoming.isNotEmpty()) {
                append("\n• ${broken.incoming.size} entry point item(s) elsewhere will stop seeing it.")
            }
            if (broken.outgoing.isNotEmpty()) {
                append("\n• ${broken.outgoing.size} of its own item(s) will stop resolving from there.")
            }
            append("\n\nMove anyway?")
        }
        return Messages.showYesNoDialog(project, message, commandName, Messages.getWarningIcon()) == Messages.YES
    }
}
