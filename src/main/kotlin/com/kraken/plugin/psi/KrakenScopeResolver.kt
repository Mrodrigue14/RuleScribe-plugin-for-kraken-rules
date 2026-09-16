package com.kraken.plugin.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.parser.KrakenTypes

/**
 * Resolves bare identifiers in a KEL expression.
 *
 * Mirrors the structure of the engine's scope model
 * (`kraken.model.project.scope.ScopeBuilder`), without types. For a rule the engine
 * stacks two scopes:
 *
 * - a global scope holding every project context, each referable by name;
 * - a nested local scope typed as the `On` target context, which makes its fields
 *   accessible without a prefix.
 *
 * `AstBuilder` adds the variables declared in the expression on top (`set x to …` and
 * the iteration variables of `for`, `every`, `some`). They shadow fields, hence the
 * order in [resolve].
 *
 * Without type inference this stops where the engine goes on: it follows `a.b.c` while
 * each link denotes a known context and gives up as soon as an expression type is
 * needed. Not resolving is better than resolving wrongly.
 */
object KrakenScopeResolver {

    /**
     * Declaration denoted by [name] at [reference], or null.
     *
     * Engine order: expression variables shadow fields of the target context, which shadow
     * context names.
     */
    fun resolve(reference: PsiElement, name: String): PsiElement? {
        declaredVariable(reference, name)?.let { return it }
        functionParameter(reference, name)?.let { return it }
        filterContext(reference)?.let { item ->
            findField(reference, item, name)?.let { return it }
        }
        targetContextName(reference)?.let { context ->
            findField(reference, context, name)?.let { return it }
        }
        return KrakenPsiUtil.findContextDecl(reference.containingFile, name)
    }

    /**
     * Context of the filtered element when [reference] is inside a `collection[…]`
     * predicate.
     *
     * This is the engine's `ScopeType.FILTER`: in `Vehicle[model = "P01"]` the predicate
     * is evaluated on a `Vehicle`. The nearest bracket wins, which handles nested filters.
     */
    fun filterContext(reference: PsiElement): String? {
        var current: PsiElement? = reference
        while (current != null && current !is KrakenRuleDecl && current !is KrakenFunctionDecl) {
            val parent = current.parent
            if (parent?.node?.elementType == KrakenTypes.BRACKET_ACCESS) {
                val chain = parent.parent?.takeIf { it.node.elementType == KrakenTypes.POSTFIX_EXPR }
                return chain?.let { contextBefore(it, parent) }
            }
            current = parent
        }
        return null
    }

    /**
     * True if [reference] is inside a `collection[…]` predicate.
     *
     * Together with a null [filterContext], this separates "the filter scope is empty" from
     * "the element type is unknown". The latter matches the engine's dynamic scopes
     * (`Scope.isDynamic`), where any reference is accepted.
     */
    fun isInFilterPredicate(reference: PsiElement): Boolean {
        var current: PsiElement? = reference
        while (current != null && current !is KrakenRuleDecl && current !is KrakenFunctionDecl) {
            if (current.parent?.node?.elementType == KrakenTypes.BRACKET_ACCESS) return true
            current = current.parent
        }
        return false
    }

    /**
     * Context denoted by the start of an access chain, up to [stopAt] excluded.
     *
     * A bracket keeps the context, since filtering or indexing selects elements of the same
     * type. A dot advances one link, and the chain stops at the first link that denotes no
     * known context.
     */
    fun contextBefore(chain: PsiElement, stopAt: PsiElement): String? {
        val head = PsiTreeUtil.findChildOfType(chain, KrakenRefExpr::class.java) ?: return null
        var context = contextDenotedBy(head, head.referenceName) ?: return null
        for (child in chain.children) {
            if (child === stopAt) return context
            if (child.node.elementType != KrakenTypes.DOT_ACCESS) continue
            val segment = PsiTreeUtil.findChildOfType(child, KrakenPathSegment::class.java) ?: return null
            if (segment === stopAt) return context
            context = contextOfField(chain, context, segment.segmentName) ?: return null
        }
        return context
    }

    /**
     * Parameter of the enclosing `Function`. The engine builds a dedicated scope for a
     * function body (`ScopeBuilder.buildFunctionScope`) whose symbols are its parameters,
     * with no target context.
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

    /** `Coverage[] coverages` → `coverages`; the name is optional when parsing. */
    private fun parameterName(param: com.intellij.lang.ASTNode): String? = KrakenPsiUtil.identifiersOf(param).takeIf { it.size >= 2 }?.last()

    /** What [resolve] would accept here, for completion. */
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
     * Context denoted by the head of an access chain, to resolve the next segment. `Policy`
     * denotes Policy, a `Child Address` field denotes Address, a scalar field denotes
     * nothing.
     */
    fun contextDenotedBy(reference: PsiElement, name: String): String? {
        targetContextName(reference)?.let { target ->
            contextOfField(reference, target, name)?.let { return it }
        }
        if (KrakenPsiUtil.findContextDecl(reference.containingFile, name) != null) return name
        return null
    }

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

    /** Declaration of [field] in [context], including fields inherited through `Is`. */
    fun findField(from: PsiElement, context: String, field: String, depth: Int = 0): PsiElement? {
        if (depth > 4) return null
        // Every same-named declaration is a candidate: the field may exist in only one of them.
        for (decl in KrakenPsiUtil.findContextDecls(from.containingFile, context)) {
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
            val inherited = decl.node.findChildByType(KrakenTypes.INHERITED_CONTEXTS) ?: continue
            for (parent in inherited.getChildren(null)) {
                if (parent.elementType == KrakenTypes.COMMA ||
                    parent.psi is com.intellij.psi.PsiWhiteSpace
                ) {
                    continue
                }
                findField(from, parent.text.trim(), field, depth + 1)?.let { return it }
            }
        }
        return null
    }

    /**
     * Context denoted by field [name] of [context], if any: `Child Address` and
     * `Address address` both denote Address, a scalar field denotes nothing. This is how an
     * `a.b.c` chain advances one link.
     */
    fun contextOfField(from: PsiElement, context: String, name: String): String? {
        val field = findField(from, context, name) ?: return null
        // `Child Address`: the child name is the context name.
        if (field.node.elementType == KrakenTypes.CHILD_DECL) return name
        // `Address address`: the first identifier is the type.
        val type = fieldDeclType(field.node) ?: return null
        return type.takeIf { KrakenPsiUtil.findContextDecl(from.containingFile, it) != null }
    }

    /**
     * Variable declared by an enclosing `set`, `for` or quantifier. A variable is only
     * visible inside the expression that declares it, which the tree already encodes.
     */
    private fun declaredVariable(reference: PsiElement, name: String): PsiElement? = variableScopes(reference).firstOrNull { variableNameOf(it) == name }?.let { scope ->
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
            // A preceding `set x to …` in the same block stays visible afterwards.
            var sibling = current.prevSibling
            while (sibling != null) {
                if (sibling.node?.elementType == KrakenTypes.SET_VAR) scopes += sibling
                sibling = sibling.prevSibling
            }
            current = current.parent
        }
        return scopes
    }

    /** Keywords introducing an expression variable, whose name is the first identifier after them. */
    private val VARIABLE_KEYWORDS = setOf(
        KrakenTypes.SET_KW,
        KrakenTypes.FOR_KW,
        KrakenTypes.EVERY_KW,
        KrakenTypes.SOME_KW,
    )

    private fun variableLeaf(scope: PsiElement): PsiElement? {
        var child = scope.node.firstChildNode
        var seenKeyword = false
        while (child != null) {
            if (child.elementType in VARIABLE_KEYWORDS) {
                seenKeyword = true
            } else if (seenKeyword && child.psi !is com.intellij.psi.PsiWhiteSpace) {
                return child.psi
            }
            child = child.treeNext
        }
        return null
    }

    private fun variableNameOf(scope: PsiElement): String? = variableLeaf(scope)?.text?.trim()?.takeIf { it.isNotEmpty() }

    /** `String policyCd` → `policyCd`: the name is the last identifier. */
    private fun fieldDeclName(field: com.intellij.lang.ASTNode): String? = KrakenPsiUtil.identifiersOf(field).lastOrNull()

    private fun fieldDeclType(field: com.intellij.lang.ASTNode): String? = KrakenPsiUtil.identifiersOf(field).takeIf { it.size >= 2 }?.first()

    /** `Child Address : path` → `Address`. */
    private fun childDeclName(child: com.intellij.lang.ASTNode): String? = KrakenPsiUtil.identifiersOf(child).firstOrNull()
}
