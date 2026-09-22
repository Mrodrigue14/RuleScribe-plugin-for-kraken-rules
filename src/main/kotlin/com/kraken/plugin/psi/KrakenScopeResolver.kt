package com.kraken.plugin.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.siblings
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
 * order of [declarationsInScope].
 *
 * Without type inference this stops where the engine goes on: it follows `a.b.c` while
 * each link denotes a known context and gives up as soon as an expression type is
 * needed. Not resolving is better than resolving wrongly.
 */
object KrakenScopeResolver {

    /** Declaration denoted by [name] at [reference], or null. */
    fun resolve(reference: PsiElement, name: String): PsiElement? = declarationsInScope(reference).firstOrNull { it.name == name }?.element

    /** What [resolve] would accept here, for completion. */
    fun visibleNames(reference: PsiElement): List<String> = declarationsInScope(reference).map { it.name }.distinct().toList()

    private class Named(val name: String, val element: PsiElement)

    /**
     * Every name visible at [reference], innermost scope first: expression variables, then
     * the parameters of the enclosing function, the fields of a filtered element, those of
     * the `On` target context, and finally context names.
     */
    private fun declarationsInScope(reference: PsiElement): Sequence<Named> = sequence {
        for (scope in variableScopes(reference)) {
            variableLeaf(scope)?.let { yield(Named(it.text, it)) }
        }
        enclosingFunction(reference)?.parameters?.forEach { parameter ->
            parameter.name?.let { yield(Named(it, parameter)) }
        }
        filterContext(reference)?.let { yieldAll(membersOf(reference, it)) }
        targetContextName(reference)?.let { yieldAll(membersOf(reference, it)) }
        for (context in KrakenContexts.findContextsVisible(reference.containingFile)) {
            context.name?.let { yield(Named(it, context)) }
        }
    }

    private fun membersOf(reference: PsiElement, context: String): Sequence<Named> = KrakenContexts.contextMembers(reference.containingFile, context)
        .mapNotNull { member -> member.name?.let { Named(it, member) } }

    /**
     * Context of the filtered element when [reference] is inside a `collection[…]`
     * predicate.
     *
     * This is the engine's `ScopeType.FILTER`: in `Vehicle[model = "P01"]` the predicate
     * is evaluated on a `Vehicle`. The nearest bracket wins, which handles nested filters.
     */
    fun filterContext(reference: PsiElement): String? = enclosingFilter(reference)?.let { contextOfFilter(it) }

    /**
     * True if [reference] is inside a `collection[…]` predicate whose element type is
     * unknown. The scope is then undetermined rather than empty: it matches the engine's
     * dynamic scopes (`Scope.isDynamic`), where any reference is accepted.
     */
    fun isInUntypedFilter(reference: PsiElement): Boolean {
        val filter = enclosingFilter(reference) ?: return false
        return contextOfFilter(filter) == null
    }

    private fun enclosingFilter(reference: PsiElement): PsiElement? = expressionAncestors(reference).firstOrNull { it.node?.elementType == KrakenTypes.BRACKET_ACCESS }

    private fun contextOfFilter(filter: PsiElement): String? {
        val chain = filter.parent?.takeIf { it.node.elementType == KrakenTypes.POSTFIX_EXPR } ?: return null
        return contextBefore(chain, filter)
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
     * The engine builds a dedicated scope for a function body
     * (`ScopeBuilder.buildFunctionScope`) whose symbols are its parameters, with no target
     * context.
     */
    private fun enclosingFunction(reference: PsiElement): KrakenFunctionDecl? = PsiTreeUtil.getParentOfType(reference, KrakenFunctionDecl::class.java, false)

    /**
     * Context denoted by the head of an access chain, to resolve the next segment. `Policy`
     * denotes Policy, a `Child Address` field denotes Address, a scalar field denotes
     * nothing.
     */
    fun contextDenotedBy(reference: PsiElement, name: String): String? {
        targetContextName(reference)?.let { target ->
            contextOfField(reference, target, name)?.let { return it }
        }
        if (KrakenContexts.contextExists(reference.containingFile, name)) return name
        return null
    }

    fun targetContextName(element: PsiElement): String? = PsiTreeUtil.getParentOfType(element, KrakenRuleDecl::class.java, false)?.targetContextLeaf()?.text

    /** Declaration of [field] in [context], including fields inherited through `Is`. */
    fun findField(from: PsiElement, context: String, field: String): KrakenContextMember? = KrakenContexts.contextMembers(from.containingFile, context).firstOrNull { it.name == field }

    /**
     * Context denoted by field [name] of [context], if any: `Child Address` and
     * `Address address` both denote Address, a scalar field denotes nothing. This is how an
     * `a.b.c` chain advances one link.
     */
    fun contextOfField(from: PsiElement, context: String, name: String): String? = when (val member = findField(from, context, name)) {
        is KrakenChildDecl -> name
        is KrakenFieldDecl -> member.typeName?.takeIf { KrakenContexts.contextExists(from.containingFile, it) }
        null -> null
    }

    /** [reference] and its ancestors, up to the enclosing rule or function excluded. */
    private fun expressionAncestors(reference: PsiElement): Sequence<PsiElement> = generateSequence(reference) { it.parent }
        .takeWhile { it !is KrakenRuleDecl && it !is KrakenFunctionDecl }

    /**
     * Enclosing `for` and quantifier expressions, and each `set x to …` declared earlier in
     * an enclosing block, innermost first. A variable is only visible inside the expression
     * that declares it, which the tree already encodes.
     */
    private fun variableScopes(reference: PsiElement): Sequence<PsiElement> = expressionAncestors(reference).flatMap { ancestor ->
        val type = ancestor.node?.elementType
        val declaring = if (type == KrakenTypes.FOR_EXPR || type == KrakenTypes.QUANTIFIER_EXPR) sequenceOf(ancestor) else emptySequence()
        declaring + ancestor.siblings(forward = false, withSelf = false).filter { it.node?.elementType == KrakenTypes.SET_VAR }
    }

    /** The variable a `set`, `for` or quantifier declares: the first identifier after its keyword. */
    private fun variableLeaf(scope: PsiElement): PsiElement? = KrakenPsiUtil.firstIdAfter(scope.node, VARIABLE_KEYWORDS)?.psi

    private val VARIABLE_KEYWORDS = TokenSet.create(
        KrakenTypes.SET_KW,
        KrakenTypes.FOR_KW,
        KrakenTypes.EVERY_KW,
        KrakenTypes.SOME_KW,
    )
}
