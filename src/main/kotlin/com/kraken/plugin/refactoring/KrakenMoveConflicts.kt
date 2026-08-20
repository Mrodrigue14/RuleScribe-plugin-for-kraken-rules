package com.kraken.plugin.refactoring

import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenPsiUtil
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
