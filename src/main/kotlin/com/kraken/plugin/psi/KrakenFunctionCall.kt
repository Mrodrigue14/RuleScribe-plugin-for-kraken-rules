package com.kraken.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafElement
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.parser.KrakenTypes

/**
 * Appel de fonction dans une expression KEL : `Round(x, 2)`, `Limits(coverages)`.
 *
 * Trois provenances possibles pour la cible, comme dans le moteur
 * (`kraken.model.project.scope.ScopeBuilder`) : une fonction native Java, une
 * `Function` déclarée avec un corps KEL, ou une signature `Function` sans corps.
 * Seules les deux dernières existent en PSI — les natives sont dans le
 * catalogue embarqué, sans source à ouvrir.
 */
class KrakenFunctionCall(node: ASTNode) : ASTWrapperPsiElement(node) {

    /** Nom appelé, c'est-à-dire tout ce qui précède la liste d'arguments. */
    val functionName: String
        get() = headRange()?.substring(text)?.trim().orEmpty()

    /**
     * Nombre d'arguments. Le moteur identifie une fonction par (nom, arité) :
     * c'est cette valeur, et non les types, qui sélectionne la surcharge.
     */
    val argumentCount: Int
        get() {
            val args = node.findChildByType(KrakenTypes.CALL_ARGS) ?: return 0
            return args.getChildren(null)
                .count { it.elementType == KrakenTypes.EXPRESSION }
        }

    override fun getReference(): PsiReference? {
        val range = headRange()?.takeIf { !it.isEmpty } ?: return null
        return KrakenFunctionReference(this, range)
    }

    /** Vrai si l'appel correspond à une fonction native ou déclarée et visible. */
    fun isResolvable(): Boolean =
        KrakenFunctionCatalog.find(functionName, argumentCount) != null ||
            KrakenPsiUtil.findFunctionVisible(this, functionName, argumentCount) != null

    /** Étendue du nom appelé, relative à l'élément. */
    private fun headRange(): TextRange? {
        val args = node.findChildByType(KrakenTypes.CALL_ARGS) ?: return null
        return TextRange(0, args.startOffset - node.startOffset)
    }
}

/**
 * Référence **souple** : une fonction native n'a pas de déclaration à ouvrir,
 * donc une résolution nulle est un cas normal, pas une erreur. C'est
 * `KrakenUnknownFunctionInspection` qui décide de ce qui est réellement inconnu,
 * en consultant aussi le catalogue.
 */
class KrakenFunctionReference(element: KrakenFunctionCall, range: TextRange) :
    PsiReferenceBase<KrakenFunctionCall>(element, range, true) {

    override fun resolve(): PsiElement? =
        KrakenPsiUtil.findFunctionVisible(element, element.functionName, element.argumentCount)

    override fun getVariants(): Array<Any> =
        KrakenPsiUtil.findFunctionsVisible(element)
            .mapNotNull { it.name }
            .distinct()
            .toTypedArray()
}

/**
 * Renommer une `Function` doit réécrire ses appels : le nom appelé n'est pas un
 * nœud propre du BNF (`call_head` est une règle privée), donc c'est le token de
 * tête qu'il faut remplacer, pas l'élément entier.
 */
class KrakenFunctionCallManipulator : AbstractElementManipulator<KrakenFunctionCall>() {

    override fun handleContentChange(
        element: KrakenFunctionCall,
        range: TextRange,
        newContent: String
    ): KrakenFunctionCall {
        val head = element.node.firstChildNode
        if (head is LeafElement) head.replaceWithText(newContent)
        return element
    }

    override fun getRangeInElement(element: KrakenFunctionCall): TextRange =
        element.reference?.rangeInElement ?: TextRange(0, element.textLength)
}
