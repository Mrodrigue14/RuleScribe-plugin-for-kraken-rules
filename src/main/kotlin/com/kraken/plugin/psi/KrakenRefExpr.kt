package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes

/**
 * Identifiant nu dans une expression KEL : `limitAmount`, `Policy`, `total`.
 *
 * Il peut désigner une variable déclarée par l'expression, un champ du contexte
 * visé par la clause `On`, ou un contexte du projet — [KrakenScopeResolver]
 * tranche, dans l'ordre du moteur.
 */
class KrakenRefExpr(node: ASTNode) : ASTWrapperPsiElement(node) {

    val referenceName: String
        get() = text.trim()

    override fun getReference(): PsiReference? =
        if (referenceName.isEmpty()) null else KrakenIdentifierReference(this)
}

/**
 * Segment d'une chaîne d'accès : le `postalCode` de `AddressInfo.postalCode`.
 *
 * Ne résout que si le segment précédent désigne un contexte connu. Sans
 * inférence de types, une tête dont le type est inconnu arrête la chaîne : on
 * préfère ne pas résoudre plutôt que de deviner.
 */
class KrakenPathSegment(node: ASTNode) : ASTWrapperPsiElement(node) {

    /** Un segment suivi d'arguments est un appel de méthode, pas un champ. */
    val isCall: Boolean
        get() = node.findChildByType(KrakenTypes.CALL_ARGS) != null

    val segmentName: String
        get() = node.firstChildNode?.text?.trim().orEmpty()

    override fun getReference(): PsiReference? =
        if (isCall || segmentName.isEmpty()) null else KrakenPathSegmentReference(this)

    /**
     * Contexte auquel appartient ce segment, déduit du maillon précédent.
     *
     * La chaîne vit dans un `postfix_expr` : `primary_expr` puis une suite de
     * `dot_access`. On repart de la tête et on avance segment par segment,
     * chacun devant désigner un contexte pour que le suivant soit résoluble.
     */
    fun owningContext(): String? {
        val access = parent?.takeIf { it.node.elementType == KrakenTypes.DOT_ACCESS } ?: return null
        val chain = access.parent?.takeIf { it.node.elementType == KrakenTypes.POSTFIX_EXPR } ?: return null
        return KrakenScopeResolver.contextBefore(chain, this)
    }
}

class KrakenIdentifierReference(element: KrakenRefExpr) :
    PsiReferenceBase<KrakenRefExpr>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? =
        KrakenScopeResolver.resolve(element, element.referenceName)

    override fun getVariants(): Array<Any> =
        KrakenScopeResolver.visibleNames(element).toTypedArray()
}

class KrakenPathSegmentReference(element: KrakenPathSegment) :
    PsiReferenceBase<KrakenPathSegment>(
        element,
        TextRange(0, element.segmentName.length),
        true
    ) {

    override fun resolve(): PsiElement? {
        val context = element.owningContext() ?: return null
        return KrakenScopeResolver.findField(element, context, element.segmentName)
    }

    override fun getVariants(): Array<Any> {
        val context = element.owningContext() ?: return emptyArray()
        return KrakenPsiUtil.contextFieldNames(element.containingFile, context).toTypedArray()
    }
}
