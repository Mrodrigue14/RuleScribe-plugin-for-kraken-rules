package com.kraken.plugin.refactoring

import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenPsiUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.psi.KrakenRuleRef

/**
 * Ce qu'un déplacement de règle casserait.
 *
 * C'est le cœur du refactoring, et la raison pour laquelle il n'est pas une
 * simple découpe de texte. Les références de règles sont **par nom** :
 * `EntryPoint "Validation" { "Ma règle" }` ne nomme aucun fichier ni aucun
 * namespace. Déplacer la déclaration ne change donc **aucun texte de
 * référence** — cela change seulement si ces références résolvent encore.
 *
 * Une règle qui part vers un namespace qu'un fichier référent ne voit pas
 * casse chacune de ses entrées sans qu'une seule ligne bouge chez lui. Un
 * refactoring qui déplacerait le texte sans poser cette question produirait
 * exactement ce genre de dégât silencieux, et c'est aussi le seul cas où le
 * DSL diffère franchement d'un langage à références nommées.
 *
 * L'axe `Import Rule` compte à part : un fichier qui importait la règle
 * depuis son ancien namespace pointe, après le déplacement, vers un namespace
 * qui ne la contient plus. Son import cesse donc de la faire résoudre, sauf
 * s'il nomme déjà le namespace de destination.
 */
object KrakenMoveConflicts {

    /**
     * Références qui résolvent aujourd'hui vers [declaration] mais ne
     * résoudraient plus si elle était déplacée dans [target].
     *
     * Vide signifie que le déplacement est sûr.
     */
    fun brokenBy(declaration: KrakenRuleDecl, target: KrakenFile): List<KrakenRuleRef> {
        val name = declaration.name ?: return emptyList()
        val targetNamespace = KrakenPsiUtil.namespaceOf(target)
        return KrakenPsiUtil.findRuleRefsVisibleTo(declaration)
            .filter { ref -> !wouldStillResolve(ref, name, target, targetNamespace) }
    }

    /**
     * Ce qu'un déplacement d'EntryPoint casserait, **dans les deux sens**.
     *
     * Une règle n'a qu'une direction : qui la référence. Un EntryPoint en a
     * deux, parce qu'il référence lui-même. Le sortir de son namespace peut
     * donc casser les entry points qui le citent *et* ses propres items, et
     * les deux comptes doivent rester séparés — accepter de casser trois
     * références chez les autres n'est pas la même décision que de vider
     * l'entry point qu'on déplace.
     */
    data class EntryPointMove(
        /** Items `EntryPoint "X"` ailleurs qui ne verraient plus la déclaration. */
        val incoming: List<KrakenEpRef>,
        /** Items de l'EntryPoint déplacé qui ne résoudraient plus depuis la destination. */
        val outgoing: List<PsiElement>,
    ) {
        val isEmpty: Boolean get() = incoming.isEmpty() && outgoing.isEmpty()
        val total: Int get() = incoming.size + outgoing.size
    }

    fun brokenBy(declaration: KrakenEntryPointDecl, target: KrakenFile): EntryPointMove {
        // Sens entrant. Pas d'axe d'import ici : `KrakenDSL.g4` ne connaît que
        // `Include` et `Import Rule` (`anImport : namespaceImport | ruleImport`),
        // donc rien ne rattrape un entry point devenu invisible.
        val incoming = KrakenPsiUtil.findEpRefsVisibleTo(declaration)
            .filter { target !in KrakenPsiUtil.visibleFiles(it.containingFile) }

        // Sens sortant. Un item qui ne résout déjà pas n'est cassé par personne.
        val outgoing = mutableListOf<PsiElement>()
        PsiTreeUtil.findChildrenOfType(declaration, KrakenRuleRef::class.java)
            .filterNotTo(outgoing) { ruleItemSurvives(it, target) }
        PsiTreeUtil.findChildrenOfType(declaration, KrakenEpRef::class.java)
            .filterNotTo(outgoing) { epItemSurvives(it, target) }
        return EntryPointMove(incoming, outgoing)
    }

    /**
     * Un item de règle résout depuis [target] si la destination voit le fichier
     * déclarant, ou si le namespace de destination importe explicitement cette
     * règle depuis celui où elle est déclarée. C'est la même question à deux
     * axes que [wouldStillResolve], posée dans l'autre sens.
     */
    private fun ruleItemSurvives(item: KrakenRuleRef, target: KrakenFile): Boolean {
        val declaration = item.reference.resolve() as? KrakenRuleDecl ?: return true
        val declarationFile = declaration.containingFile as? KrakenFile ?: return true
        if (KrakenPsiUtil.visibleFiles(target).any { it.isEquivalentTo(declarationFile) }) return true
        val declarationNamespace = KrakenPsiUtil.namespaceOf(declarationFile)
        return KrakenPsiUtil.ruleImportsForNamespaceOf(target)
            .any { it.ruleName == item.ruleName && it.sourceNamespace == declarationNamespace }
    }

    /** Un entry point imbriqué n'a que l'axe de visibilité. */
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
        // La destination est-elle dans la portée du fichier référent ?
        if (target in KrakenPsiUtil.visibleFiles(refFile)) return true
        // Sinon, un import explicite depuis le namespace de destination suffit.
        return KrakenPsiUtil.ruleImportsForNamespaceOf(refFile)
            .any { it.ruleName == name && it.sourceNamespace == targetNamespace }
    }
}
