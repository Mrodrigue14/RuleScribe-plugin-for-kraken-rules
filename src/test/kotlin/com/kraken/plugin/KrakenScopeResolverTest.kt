package com.kraken.plugin

import com.intellij.psi.PsiElement
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenScopeResolver

/**
 * Identifier scopes in a KEL expression.
 *
 * For a rule the engine stacks a global scope holding every project context and a local
 * scope typed as the `On` target, whose fields need no prefix
 * (`ScopeBuilder.doBuildScope`). Expression variables (`set`, `for`, `every`, `some`)
 * stack on top and shadow the rest.
 *
 * These tests pin that order, especially where resolution must fail: without type
 * inference, resolving wrongly is worse than not resolving at all.
 */
class KrakenScopeResolverTest : KrakenRuleBodyTestCase() {

    override val model = """
        Root Context Policy {
            String policyCd
            Money limitAmount
            Child AddressInfo
        }

        Context AddressInfo {
            String postalCode
        }

        Context Coverage Is Insurable {
            Money limit
        }

        Context Insurable {
            String inheritedCd
        }
    """.trimIndent()

    private fun resolve(name: String): PsiElement? = KrakenScopeResolver.resolve(refNamed(name), name)

    fun testFieldOfTargetContextResolvesWithoutPrefix() {
        configureRule("Assert limitAmount > 0")
        val target = resolve("limitAmount")
        assertNotNull("A field of the On target is directly referable", target)
        assertEquals(KrakenTypes.FIELD_DECL, target!!.node.elementType)
        assertTrue(target.text.contains("Money limitAmount"))
    }

    fun testInheritedFieldResolvesThroughIs() {
        myFixture.configureByText(
            "inherited.rules",
            """
            $model

            Rule "On a subtype" On Coverage.limit {
                Assert inheritedCd != null
            }
            """.trimIndent(),
        )
        assertNotNull(
            "Is Insurable brings its fields into scope",
            KrakenScopeResolver.resolve(refNamed("inheritedCd"), "inheritedCd"),
        )
    }

    fun testUnknownNameResolvesToNothing() {
        configureRule("Assert notAField > 0")
        assertNull(resolve("notAField"))
    }

    fun testContextNameResolvesToItsDeclaration() {
        // Coverage is not a child of Policy, so the name can only come from the global scope.
        configureRule("Assert Coverage != null")
        val target = resolve("Coverage")
        assertNotNull(target)
        assertEquals(KrakenTypes.CONTEXT_DECL, target!!.node.elementType)
    }

    /**
     * `Policy` declares `Child AddressInfo`, so the name is a field of the target context
     * and the local scope wins: it resolves to the child, not to the context declaration.
     */
    fun testChildShadowsTheContextOfTheSameName() {
        configureRule("Assert AddressInfo != null")
        assertEquals(KrakenTypes.CHILD_DECL, resolve("AddressInfo")!!.node.elementType)
    }

    fun testIterationVariableOfForIsInScope() {
        configureRule("Assert (for c in coverages return c) != null")
        val target = resolve("c")
        assertNotNull("The for variable is declared by the expression itself", target)
        assertEquals("c", target!!.text)
    }

    fun testQuantifierVariableIsInScope() {
        configureRule("Assert every item in items satisfies item != null")
        assertNotNull(resolve("item"))
    }

    fun testSetVariableIsVisibleAfterItsDeclaration() {
        configureRule("Assert set total to limitAmount return total > 0")
        val target = resolve("total")
        assertNotNull("set declares a variable usable in the return", target)
        assertEquals("total", target!!.text)
    }

    /**
     * A variable shadows a same-named field, as in the engine. The name after `every` is
     * not a reference, so the only REF_EXPR in the file is the one in `satisfies`.
     */
    fun testVariableShadowsAFieldOfTheSameName() {
        configureRule("Assert every limitAmount in coverages satisfies limitAmount > 0")
        val target = resolve("limitAmount")
        assertNotNull(target)
        assertFalse(
            "The quantifier variable wins over Policy.limitAmount",
            target!!.node.elementType == KrakenTypes.FIELD_DECL,
        )
    }

    fun testContextNameDenotesItselfForTheNextSegment() {
        configureRule("Assert AddressInfo.postalCode != null")
        assertEquals("AddressInfo", KrakenScopeResolver.contextDenotedBy(refNamed("AddressInfo"), "AddressInfo"))
    }

    fun testChildFieldDenotesItsContext() {
        configureRule("Assert AddressInfo.postalCode != null")
        // `Child AddressInfo` in Policy: the child name is the context.
        assertEquals(
            "AddressInfo",
            KrakenScopeResolver.contextDenotedBy(refNamed("AddressInfo"), "AddressInfo"),
        )
    }

    /** A scalar field denotes no context, so the chain stops. */
    fun testScalarFieldDenotesNoContext() {
        configureRule("Assert policyCd != null")
        assertNull(KrakenScopeResolver.contextDenotedBy(refNamed("policyCd"), "policyCd"))
    }

    /**
     * In `Coverage[limit > 0]` the predicate is evaluated on a `Coverage`'s fields: the
     * engine's `ScopeType.FILTER`. Found on the real kraken-rules corpus, where it caused
     * all of the inspection's false positives.
     */
    fun testFilterPredicateSeesTheItemFields() {
        configureRule("Assert Count(Coverage[limit > 0]) = 1")
        val target = resolve("limit")
        assertNotNull("The predicate sees the filtered element's fields", target)
        assertEquals(KrakenTypes.FIELD_DECL, target!!.node.elementType)
    }

    fun testNestedFilterUsesTheNearestBracket() {
        configureRule("Assert Count(AddressInfo[postalCode = Count(Coverage[limit > 0])]) = 1")
        assertNotNull("postalCode vient d'AddressInfo", resolve("postalCode"))
        assertNotNull("limit vient de Coverage", resolve("limit"))
    }

    /** A bracket keeps the context: filtering preserves the type. */
    fun testChainContinuesAfterAFilter() {
        configureRule("Assert AddressInfo[postalCode != null].postalCode != null")
        assertNotNull(resolve("postalCode"))
    }

    /** Unknown head: the filter scope is undetermined, not empty. */
    fun testFilterOnAnUnknownHeadHasNoContext() {
        configureRule("Assert IsEmpty(context.additional.items[whatever = 1])")
        val ref = refNamed("whatever")
        assertTrue(KrakenScopeResolver.isInUntypedFilter(ref))
        assertNull(KrakenScopeResolver.filterContext(ref))
    }

    fun testVisibleNamesCoverVariablesFieldsAndContexts() {
        configureRule("Assert set tmp to 1 return tmp > 0")
        val names = KrakenScopeResolver.visibleNames(refNamed("tmp"))
        assertTrue("variable", names.contains("tmp"))
        assertTrue("field of the target context", names.contains("policyCd"))
        assertTrue("visible context", names.contains("AddressInfo"))
    }
}
