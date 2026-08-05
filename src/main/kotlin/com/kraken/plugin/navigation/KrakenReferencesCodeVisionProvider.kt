package com.kraken.plugin.navigation

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenPsiUtil
import com.kraken.plugin.psi.KrakenRuleDecl

/**
 * Inlay « N usages » au-dessus des déclarations, cliquable.
 *
 * [ReferencesCodeVisionProvider] fournit déjà le clic — il ouvre la popup
 * standard d'usages, groupée par fichier et avec l'aperçu du code — ainsi que
 * le libellé et le groupe de réglages. Il ne reste donc que le décompte.
 *
 * Ce décompte passe par [KrakenPsiUtil], donc par le modèle de visibilité mis
 * en cache : une référence dont le namespace ne voit pas la déclaration n'est
 * pas un usage, exactement comme pour Find Usages et l'inspection de règle
 * inutilisée. Les trois affichent le même nombre parce qu'ils appellent les
 * mêmes fonctions.
 */
class KrakenReferencesCodeVisionProvider : ReferencesCodeVisionProvider() {

    override fun acceptsFile(file: PsiFile): Boolean = file is KrakenFile

    override fun acceptsElement(element: PsiElement): Boolean =
        element is KrakenRuleDecl || element is KrakenEntryPointDecl || element is KrakenFunctionDecl

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        val usages = when (element) {
            is KrakenRuleDecl -> KrakenPsiUtil.findRuleRefsVisibleTo(element).size
            is KrakenEntryPointDecl -> KrakenPsiUtil.findEpRefsVisibleTo(element).size
            is KrakenFunctionDecl -> KrakenPsiUtil.findFunctionCallsVisibleTo(element).size
            else -> return null
        }
        return when (usages) {
            0 -> "no usages"
            1 -> "1 usage"
            else -> "$usages usages"
        }
    }

    override val id: String = ID

    /** Seul inlay du plugin : l'ordre relatif n'a rien à départager. */
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

    companion object {
        const val ID: String = "kraken.references"
    }
}
