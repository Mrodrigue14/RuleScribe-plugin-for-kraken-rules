package com.kraken.plugin.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes

/**
 * Résolution des identifiants nus dans une expression KEL.
 *
 * Reproduit *structurellement* le modèle de portées du moteur
 * (`kraken.model.project.scope.ScopeBuilder`), sans les types. Le moteur
 * empile deux portées pour une règle :
 *
 * - une portée **GLOBALE** dont la table des symboles contient tous les
 *   contextes du projet, chacun référençable par son nom (référence
 *   inter-contextes) ;
 * - une portée **LOCALE** imbriquée dedans, dont le type est le contexte visé
 *   par la clause `On` — ce qui rend ses champs accessibles sans préfixe.
 *
 * `AstBuilder` ajoute par-dessus les variables déclarées dans l'expression :
 * `set x to …` et les variables d'itération de `for` / `every` / `some`. Elles
 * masquent les champs, d'où l'ordre de [resolve].
 *
 * Sans inférence de types, ce résolveur s'arrête là où le moteur continue :
 * il suit une chaîne `a.b.c` tant que chaque maillon désigne un contexte
 * connu, et renonce dès qu'il faudrait connaître le type d'une expression.
 * C'est volontaire — mieux vaut ne pas résoudre que résoudre à tort.
 */
object KrakenScopeResolver {

    /**
     * Déclaration désignée par [name] à l'endroit de [reference], ou null.
     *
     * L'ordre suit celui du moteur : les variables de l'expression masquent les
     * champs du contexte cible, qui masquent les noms de contextes.
     */
    fun resolve(reference: PsiElement, name: String): PsiElement? {
        declaredVariable(reference, name)?.let { return it }
        functionParameter(reference, name)?.let { return it }
        targetContextName(reference)?.let { context ->
            findField(reference, context, name)?.let { return it }
        }
        return KrakenPsiUtil.findContextDecl(reference.containingFile, name)
    }

    /**
     * Paramètre de la `Function` englobante. Le moteur construit une portée
     * dédiée au corps d'une fonction (`ScopeBuilder.buildFunctionScope`), dont
     * les symboles sont ses paramètres — il n'y a ni contexte cible ni champ
     * accessible sans préfixe là-dedans.
     */
    private fun functionParameter(reference: PsiElement, name: String): PsiElement? {
        val function = PsiTreeUtil.getParentOfType(reference, KrakenFunctionDecl::class.java, false)
            ?: return null
        val params = function.node.findChildByType(KrakenTypes.FUNCTION_PARAMS) ?: return null
        return params.getChildren(null)
            .filter { it.elementType == KrakenTypes.FUNCTION_PARAM }
            .firstOrNull { parameterName(it) == name }
            ?.psi
    }

    /** `Coverage[] coverages` → `coverages`. Le nom est facultatif au parsing. */
    private fun parameterName(param: com.intellij.lang.ASTNode): String? =
        identifiersOf(param).takeIf { it.size >= 2 }?.last()

    /** Ce que [resolve] proposerait ici, pour la complétion. */
    fun visibleNames(reference: PsiElement): List<String> {
        val names = LinkedHashSet<String>()
        variableScopes(reference).mapNotNullTo(names) { variableNameOf(it) }
        PsiTreeUtil.getParentOfType(reference, KrakenFunctionDecl::class.java, false)
            ?.node?.findChildByType(KrakenTypes.FUNCTION_PARAMS)
            ?.getChildren(null)
            ?.filter { it.elementType == KrakenTypes.FUNCTION_PARAM }
            ?.mapNotNullTo(names) { parameterName(it) }
        targetContextName(reference)?.let {
            names.addAll(KrakenPsiUtil.contextFieldNames(reference.containingFile, it))
        }
        names.addAll(KrakenPsiUtil.findContextNamesVisible(reference.containingFile))
        return names.toList()
    }

    /**
     * Contexte désigné par une tête de chaîne d'accès, pour résoudre le segment
     * suivant. `Policy` désigne le contexte Policy ; un champ `Child Address`
     * désigne le contexte Address ; un champ scalaire ne désigne rien.
     */
    fun contextDenotedBy(reference: PsiElement, name: String): String? {
        targetContextName(reference)?.let { target ->
            contextOfField(reference, target, name)?.let { return it }
        }
        if (KrakenPsiUtil.findContextDecl(reference.containingFile, name) != null) return name
        return null
    }

    /** Nom du contexte visé par la clause `On` de la règle englobante. */
    fun targetContextName(element: PsiElement): String? {
        val rule = PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false) ?: return null
        val target = rule.node.findChildByType(KrakenTypes.RULE_TARGET) ?: return null
        var child = target.firstChildNode
        while (child != null) {
            if (child.elementType != KrakenTypes.ON_KW &&
                child.psi !is com.intellij.psi.PsiWhiteSpace &&
                child.elementType != KrakenTypes.DOT
            ) {
                return child.text.trim().takeIf { it.isNotEmpty() }
            }
            child = child.treeNext
        }
        return null
    }

    /** Déclaration du champ [field] de [context], héritage `Is` compris. */
    fun findField(from: PsiElement, context: String, field: String, depth: Int = 0): PsiElement? {
        if (depth > 4) return null
        val decl = KrakenPsiUtil.findContextDecl(from.containingFile, context) ?: return null
        var child = decl.node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                KrakenTypes.FIELD_DECL ->
                    if (fieldDeclName(child) == field) return child.psi
                KrakenTypes.CHILD_DECL ->
                    if (childDeclName(child) == field) return child.psi
            }
            child = child.treeNext
        }
        val inherited = decl.node.findChildByType(KrakenTypes.INHERITED_CONTEXTS) ?: return null
        for (parent in inherited.getChildren(null)) {
            if (parent.elementType == KrakenTypes.COMMA || parent.psi is com.intellij.psi.PsiWhiteSpace) continue
            findField(from, parent.text.trim(), field, depth + 1)?.let { return it }
        }
        return null
    }

    /**
     * Contexte désigné par le champ [name] de [context], le cas échéant :
     * `Child Address` désigne Address, `Address address` aussi, un champ
     * scalaire ne désigne rien. C'est ce qui permet d'avancer d'un maillon
     * dans une chaîne `a.b.c`.
     */
    fun contextOfField(from: PsiElement, context: String, name: String): String? {
        val field = findField(from, context, name) ?: return null
        // `Child Address` : le nom de l'enfant EST le nom du contexte.
        if (field.node.elementType == KrakenTypes.CHILD_DECL) return name
        // `Address address` : le premier identifiant est le type.
        val type = fieldDeclType(field.node) ?: return null
        return type.takeIf { KrakenPsiUtil.findContextDecl(from.containingFile, it) != null }
    }

    /**
     * Variable déclarée par un `set`, un `for` ou un quantificateur englobant.
     * On remonte l'arbre : une variable n'est visible que dans l'expression qui
     * la déclare, ce que la structure de l'arbre exprime déjà.
     */
    private fun declaredVariable(reference: PsiElement, name: String): PsiElement? =
        variableScopes(reference).firstOrNull { variableNameOf(it) == name }?.let { scope ->
            variableLeaf(scope)
        }

    private fun variableScopes(reference: PsiElement): List<PsiElement> {
        val scopes = mutableListOf<PsiElement>()
        var current: PsiElement? = reference
        while (current != null && current !is KrakenRuleDecl && current !is KrakenFunctionDecl) {
            val type = current.node?.elementType
            if (type == KrakenTypes.FOR_EXPR || type == KrakenTypes.QUANTIFIER_EXPR) {
                scopes += current
            }
            // Un `set x to …` précédent dans le même bloc reste visible après.
            var sibling = current.prevSibling
            while (sibling != null) {
                if (sibling.node?.elementType == KrakenTypes.SET_VAR) scopes += sibling
                sibling = sibling.prevSibling
            }
            current = current.parent
        }
        return scopes
    }

    /** Le nom déclaré est le premier identifiant après le mot-clé introducteur. */
    private fun variableLeaf(scope: PsiElement): PsiElement? {
        val keywords = setOf(
            KrakenTypes.SET_KW, KrakenTypes.FOR_KW,
            KrakenTypes.EVERY_KW, KrakenTypes.SOME_KW
        )
        var child = scope.node.firstChildNode
        var seenKeyword = false
        while (child != null) {
            if (child.elementType in keywords) {
                seenKeyword = true
            } else if (seenKeyword && child.psi !is com.intellij.psi.PsiWhiteSpace) {
                return child.psi
            }
            child = child.treeNext
        }
        return null
    }

    private fun variableNameOf(scope: PsiElement): String? =
        variableLeaf(scope)?.text?.trim()?.takeIf { it.isNotEmpty() }

    /** `String policyCd` → `policyCd` : le nom est le second identifiant. */
    private fun fieldDeclName(field: com.intellij.lang.ASTNode): String? =
        identifiersOf(field).lastOrNull()

    private fun fieldDeclType(field: com.intellij.lang.ASTNode): String? =
        identifiersOf(field).takeIf { it.size >= 2 }?.first()

    /** `Child Address : path` → `Address`. */
    private fun childDeclName(child: com.intellij.lang.ASTNode): String? =
        identifiersOf(child).firstOrNull()

    /** Identifiants d'une déclaration, en s'arrêtant avant la navigation `: …`. */
    private fun identifiersOf(node: com.intellij.lang.ASTNode): List<String> {
        val names = mutableListOf<String>()
        var child = node.firstChildNode
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
