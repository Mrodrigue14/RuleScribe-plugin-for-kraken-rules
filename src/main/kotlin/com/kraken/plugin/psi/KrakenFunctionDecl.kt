package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes

/**
 * Déclaration `Function Nom(Type param) : TypeRetour { corps }`.
 *
 * Deux formes, toutes deux légitimes côté moteur :
 * - **avec corps** : l'implémentation est écrite en KEL ;
 * - **sans corps** : c'est une *signature*, qui déclare qu'une fonction Java
 *   correspondante est enregistrée. `KrakenProjectConverter` fait échouer la
 *   construction du projet si aucune ne correspond ([hasBody] renvoie faux).
 *
 * Le nom seul ne suffit pas à identifier une fonction : le moteur l'indexe par
 * `(nom, nombre de paramètres)` — voir `FunctionHeader`. D'où [arity].
 */
class KrakenFunctionDecl(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? = nameLeaf()?.psi

    override fun getName(): String? = nameLeaf()?.text

    override fun setName(name: String): PsiElement {
        val leaf = nameLeaf()
        if (leaf is LeafElement) leaf.replaceWithText(name)
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    /** Nombre de paramètres déclarés — l'identité de la fonction avec le nom. */
    val arity: Int
        get() = node.findChildByType(KrakenTypes.FUNCTION_PARAMS)
            ?.getChildren(null)
            ?.count { it.elementType == KrakenTypes.FUNCTION_PARAM }
            ?: 0

    val parameters: List<String>
        get() = node.findChildByType(KrakenTypes.FUNCTION_PARAMS)
            ?.getChildren(null)
            ?.filter { it.elementType == KrakenTypes.FUNCTION_PARAM }
            ?.map { it.text.trim() }
            .orEmpty()

    val returnType: String?
        get() = node.findChildByType(KrakenTypes.RETURN_TYPE)
            ?.findChildByType(KrakenTypes.TYPE_REF)
            ?.text
            ?.trim()

    /** Faux pour une signature nue, qui délègue son implémentation à Java. */
    fun hasBody(): Boolean = node.findChildByType(KrakenTypes.FUNCTION_BODY) != null

    /** `Limits(Coverage[] coverages) : Number[]` */
    fun signature(): String {
        val head = "${name.orEmpty()}(${parameters.joinToString(", ")})"
        return returnType?.let { "$head : $it" } ?: head
    }

    /** Commentaire de doc qui précède immédiatement la déclaration, s'il y en a un. */
    fun docComment(): PsiElement? =
        PsiTreeUtil.skipWhitespacesBackward(this)
            ?.takeIf { it.node.elementType == KrakenTypes.DOC_COMMENT }

    override fun getPresentation(): ItemPresentation = KrakenPresentations.of(
        this,
        signature(),
        KrakenPresentations.FUNCTION_ICON,
    )

    /**
     * Le nom est le dernier token avant la parenthèse ouvrante : `id` est une
     * règle privée du BNF, elle ne produit donc pas de nœud propre, et les
     * bornes génériques (`Function <T is X> Nom(…)`) s'intercalent avant lui.
     */
    private fun nameLeaf(): ASTNode? {
        val paren = node.findChildByType(KrakenTypes.LPAREN) ?: return null
        var candidate = paren.treePrev
        while (candidate != null && candidate.psi is com.intellij.psi.PsiWhiteSpace) {
            candidate = candidate.treePrev
        }
        return candidate?.takeIf { it.elementType != KrakenTypes.GENERIC_BOUNDS }
    }
}
