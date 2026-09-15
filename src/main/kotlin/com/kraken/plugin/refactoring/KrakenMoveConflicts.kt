package com.kraken.plugin.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.psi.KrakenRuleRef

/**
 * What moving a declaration would break.
 *
 * Rule references are by name: `EntryPoint "Validation" { "My rule" }` names no file
 * and no namespace. Moving the declaration changes no reference text, only whether
 * references still resolve, so a rule moved to a namespace that a referencing file
 * cannot see silently breaks every one of its items.
 *
 * `Import Rule` is a separate axis: a file importing the rule from its old namespace
 * points, after the move, to a namespace that no longer contains it, unless it already
 * names the destination namespace.
 */
object KrakenMoveConflicts {

    /**
     * References that resolve to [declaration] today but would not after moving it to
     * [target]. Empty means the move is safe.
     */
    fun brokenBy(declaration: KrakenRuleDecl, target: KrakenFile): List<KrakenRuleRef> {
        val name = declaration.name ?: return emptyList()
        val targetNamespace = KrakenPsiUtil.namespaceOf(target)
        return KrakenPsiUtil.findRuleRefsVisibleTo(declaration)
            .filter { ref -> !wouldStillResolve(ref, name, target, targetNamespace) }
    }

    /**
     * What moving an EntryPoint would break, in both directions.
     *
     * A rule is only referenced; an EntryPoint is referenced and also references. Moving it
     * can break the entry points that cite it and its own items. The two counts stay
     * separate because breaking references elsewhere and emptying the moved entry point are
     * different decisions.
     */
    data class EntryPointMove(
        /** `EntryPoint "X"` items elsewhere that would no longer see the declaration. */
        val incoming: List<KrakenEpRef>,
        /** Items of the moved EntryPoint that would no longer resolve from the destination. */
        val outgoing: List<PsiElement>,
    ) {
        val isEmpty: Boolean get() = incoming.isEmpty() && outgoing.isEmpty()
        val total: Int get() = incoming.size + outgoing.size
    }

    fun brokenBy(declaration: KrakenEntryPointDecl, target: KrakenFile): EntryPointMove {
        // Incoming. There is no import axis here: `KrakenDSL.g4` only has `Include` and
        // `Import Rule` (`anImport : namespaceImport | ruleImport`), so nothing rescues an entry
        // point that became invisible.
        val incoming = KrakenPsiUtil.findEpRefsVisibleTo(declaration)
            .filter { target !in KrakenPsiUtil.visibleFiles(it.containingFile) }

        // Outgoing. An item that already fails to resolve is not broken by the move.
        val outgoing = mutableListOf<PsiElement>()
        PsiTreeUtil.findChildrenOfType(declaration, KrakenRuleRef::class.java)
            .filterNotTo(outgoing) { ruleItemSurvives(it, target) }
        PsiTreeUtil.findChildrenOfType(declaration, KrakenEpRef::class.java)
            .filterNotTo(outgoing) { epItemSurvives(it, target) }
        return EntryPointMove(incoming, outgoing)
    }

    /**
     * A rule item resolves from [target] if the destination sees the declaring file, or if
     * the destination namespace explicitly imports the rule from its declaring namespace:
     * the same two-axis question as [wouldStillResolve], asked the other way round.
     */
    private fun ruleItemSurvives(item: KrakenRuleRef, target: KrakenFile): Boolean {
        val declaration = item.reference.resolve() as? KrakenRuleDecl ?: return true
        val declarationFile = declaration.containingFile as? KrakenFile ?: return true
        if (KrakenPsiUtil.visibleFiles(target).any { it.isEquivalentTo(declarationFile) }) return true
        val declarationNamespace = KrakenPsiUtil.namespaceOf(declarationFile)
        return KrakenPsiUtil.ruleImportsForNamespaceOf(target)
            .any { it.ruleName == item.ruleName && it.sourceNamespace == declarationNamespace }
    }

    /** A nested entry point item only has the visibility axis. */
    private fun epItemSurvives(item: KrakenEpRef, target: KrakenFile): Boolean {
        val declarationFile = item.reference?.resolve()?.containingFile ?: return true
        return KrakenPsiUtil.visibleFiles(target).any { it.isEquivalentTo(declarationFile) }
    }

    private fun wouldStillResolve(
        ref: KrakenRuleRef,
        name: String,
        target: KrakenFile,
        targetNamespace: String?,
    ): Boolean {
        val refFile = ref.containingFile
        if (target in KrakenPsiUtil.visibleFiles(refFile)) return true
        // Otherwise an explicit import from the destination namespace is enough.
        return KrakenPsiUtil.ruleImportsForNamespaceOf(refFile)
            .any { it.ruleName == name && it.sourceNamespace == targetNamespace }
    }
}
