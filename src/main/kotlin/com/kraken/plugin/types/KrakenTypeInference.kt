package com.kraken.plugin.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenPathSegment
import com.kraken.plugin.psi.KrakenRefExpr

/**
 * Déduit le type d'une expression KEL.
 *
 * Volontairement partielle. Le moteur type tout parce qu'il possède le modèle
 * résolu ; ici, chaque cas non couvert renvoie [KrakenType.Unknown], et les
 * vérifications qui le rencontrent s'abstiennent. C'est la même discipline
 * qu'en v0.9.0 : ne rien affirmer plutôt qu'affirmer à tort.
 */
object KrakenTypeInference {

    fun typeOf(element: PsiElement?): KrakenType {
        if (element == null) return KrakenType.Unknown
        return when (element.node?.elementType) {
            KrakenTypes.EXPRESSION, KrakenTypes.VALUE_CHAIN -> typeOfChain(element)
            KrakenTypes.POSTFIX_EXPR -> typeOfPostfix(element)
            KrakenTypes.GROUP_EXPR -> typeOf(singleExpressionIn(element))
            KrakenTypes.FUNCTION_CALL -> typeOfCall(element as? KrakenFunctionCall)
            KrakenTypes.REF_EXPR -> typeOfDeclaration((element as KrakenRefExpr).reference?.resolve())
            KrakenTypes.STRING -> KrakenType.String
            // Le lexer range les littéraux de date sous NUMBER_LIT : c'est le
            // texte qui les distingue, pas le type de token.
            KrakenTypes.NUMBER_LIT -> literalType(element.text)
            KrakenTypes.TRUE_KW, KrakenTypes.FALSE_KW -> KrakenType.Boolean
            KrakenTypes.NULL_KW -> KrakenType.Any
            else -> KrakenType.Unknown
        }
    }

    private fun literalType(text: String): KrakenType = when {
        DATETIME_LITERAL.matches(text) -> KrakenType.DateTime
        DATE_LITERAL.matches(text) -> KrakenType.Date
        else -> KrakenType.Number
    }

    private val DATE_LITERAL = Regex("""\d{4}-\d{2}-\d{2}""")
    private val DATETIME_LITERAL = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z?""")

    /**
     * Une chaîne de valeurs n'a un type sûr que si elle ne contient **aucun**
     * opérateur : sinon il faudrait modéliser chaque opérateur, ce que cette
     * version ne fait pas.
     */
    private fun typeOfChain(element: PsiElement): KrakenType {
        // On parcourt les nœuds AST, pas `children` : celui-ci exclut les
        // feuilles, donc les opérateurs et les littéraux y seraient invisibles
        // — une chaîne `policyCd + 1` passerait pour un simple String.
        val parts = significantChildren(element)
        if (parts.any { isOperator(it) }) return KrakenType.Unknown
        return parts.singleOrNull()?.let { typeOf(it) } ?: KrakenType.Unknown
    }

    /** Enfants AST utiles : ni blancs, ni commentaires. */
    fun significantChildren(element: PsiElement): List<PsiElement> =
        element.node.getChildren(null)
            .filter { it.psi !is com.intellij.psi.PsiWhiteSpace && it.psi !is com.intellij.psi.PsiComment }
            .map { it.psi }

    fun isOperator(element: PsiElement): Boolean = when (element.node?.elementType) {
        KrakenTypes.OP, KrakenTypes.LT, KrakenTypes.GT, KrakenTypes.STAR, KrakenTypes.COLON,
        KrakenTypes.IN_KW, KrakenTypes.IS_KW, KrakenTypes.AND_KW, KrakenTypes.OR_KW,
        KrakenTypes.INSTANCEOF_KW, KrakenTypes.TYPEOF_KW, KrakenTypes.SATISFIES_KW,
        KrakenTypes.MATCHES_KW -> true
        else -> false
    }

    /**
     * `a.b.c` : le type est celui du dernier segment résolu — mais projeté sur
     * une collection, il devient lui-même une collection. En KEL,
     * `coverages.limitAmount` sur `Coverage[]` vaut `Money[]`, pas `Money`.
     */
    private fun typeOfPostfix(element: PsiElement): KrakenType {
        // Segments **directs** de cette chaîne seulement. findChildrenOfType
        // descend récursivement : il ramènerait aussi les segments situés dans
        // les arguments d'un appel, et `Count(Vehicle.model)` prendrait alors le
        // type de `model` au lieu du type de retour de `Count`.
        val segments = directSegments(element)
        if (segments.isNotEmpty()) {
            val last = segments.last()
            if (last.isCall) return KrakenType.Unknown
            val leafType = typeOfDeclaration(last.reference?.resolve())
            if (!leafType.isKnown) return KrakenType.Unknown
            return if (projectsOverACollection(element, last)) wrap(leafType) else leafType
        }
        val head = significantChildren(element).firstOrNull() ?: return KrakenType.Unknown
        // Un crochet réduit une collection à son élément.
        val bracketed = significantChildren(element)
            .any { it.node.elementType == KrakenTypes.BRACKET_ACCESS }
        val headType = typeOf(head)
        return if (bracketed && headType is KrakenType.Array) headType.element else headType
    }

    private fun wrap(type: KrakenType): KrakenType =
        if (type is KrakenType.Array) type else KrakenType.Array(type)

    /**
     * Vrai si un maillon **avant** [last] désigne une collection : la chaîne
     * est alors une projection, et son résultat est une collection.
     */
    private fun projectsOverACollection(chain: PsiElement, last: KrakenPathSegment): Boolean {
        val head = significantChildren(chain).firstOrNull()
        if (head != null && head !is KrakenPathSegment && typeOf(head) is KrakenType.Array) return true
        return directSegments(chain)
            .takeWhile { it !== last }
            .any { !it.isCall && typeOfDeclaration(it.reference?.resolve()) is KrakenType.Array }
    }

    /** Segments d'accès appartenant à cette chaîne, sans descendre dans les appels. */
    private fun directSegments(chain: PsiElement): List<KrakenPathSegment> =
        significantChildren(chain)
            .filter { it.node.elementType == KrakenTypes.DOT_ACCESS }
            .mapNotNull { access ->
                significantChildren(access).filterIsInstance<KrakenPathSegment>().firstOrNull()
            }

    private fun typeOfCall(call: KrakenFunctionCall?): KrakenType {
        if (call == null) return KrakenType.Unknown
        KrakenFunctionCatalog.find(call.functionName, call.argumentCount)?.let {
            return KrakenType.fromDslName(it.returnType)
        }
        val declared = call.reference?.resolve() as? KrakenFunctionDecl ?: return KrakenType.Unknown
        return declared.returnType?.let { KrakenType.fromDslName(it) } ?: KrakenType.Unknown
    }

    /**
     * Type d'une déclaration ciblée par une référence : champ de contexte,
     * enfant, paramètre de fonction. Une variable d'expression (`set`, `for`)
     * n'est pas typée ici — il faudrait remonter à l'expression source.
     */
    fun typeOfDeclaration(declaration: PsiElement?): KrakenType {
        val node = declaration?.node ?: return KrakenType.Unknown
        return when (node.elementType) {
            KrakenTypes.FIELD_DECL -> fieldType(declaration)
            // `Child Address` et `Child* Address` : le nom est le contexte, et
            // l'étoile en fait une collection.
            KrakenTypes.CHILD_DECL -> {
                val name = identifiersOf(declaration).firstOrNull() ?: return KrakenType.Unknown
                val context = KrakenType.Context(name)
                if (node.findChildByType(KrakenTypes.STAR) != null) KrakenType.Array(context) else context
            }
            KrakenTypes.FUNCTION_PARAM ->
                identifiersOf(declaration).firstOrNull()
                    ?.let { KrakenType.fromDslName(it + arraySuffix(declaration)) }
                    ?: KrakenType.Unknown
            KrakenTypes.CONTEXT_DECL -> KrakenType.Unknown
            else -> KrakenType.Unknown
        }
    }

    /** `Money limitAmount` → Money ; `Coverage* items` → Coverage[]. */
    private fun fieldType(field: PsiElement): KrakenType {
        val names = identifiersOf(field)
        if (names.size < 2) return KrakenType.Unknown
        val base = KrakenType.fromDslName(names.first())
        return if (field.node.findChildByType(KrakenTypes.STAR) != null) KrakenType.Array(base) else base
    }

    private fun arraySuffix(param: PsiElement): String =
        if (param.node.findChildByType(KrakenTypes.LBRACKET) != null) "[]" else ""

    private fun singleExpressionIn(group: PsiElement): PsiElement? =
        significantChildren(group).singleOrNull { it.node.elementType == KrakenTypes.EXPRESSION }

    /** Identifiants d'une déclaration, en s'arrêtant avant la navigation `: …`. */
    private fun identifiersOf(element: PsiElement): List<String> {
        val names = mutableListOf<String>()
        var child = element.node.firstChildNode
        while (child != null) {
            when {
                child.elementType == KrakenTypes.COLON -> return names
                child.elementType == KrakenTypes.ANNOTATION -> Unit
                child.psi is com.intellij.psi.PsiWhiteSpace -> Unit
                child.elementType == KrakenTypes.STAR -> Unit
                child.elementType == KrakenTypes.LBRACKET -> Unit
                child.elementType == KrakenTypes.RBRACKET -> Unit
                child.elementType == KrakenTypes.CHILD_KW -> Unit
                child.elementType == KrakenTypes.EXTERNAL_KW -> Unit
                else -> child.text.trim().takeIf { it.isNotEmpty() }?.let { names += it }
            }
            child = child.treeNext
        }
        return names
    }
}
