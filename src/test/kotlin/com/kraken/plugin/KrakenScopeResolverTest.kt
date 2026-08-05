package com.kraken.plugin

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.KrakenScopeResolver

/**
 * Portées des identifiants dans une expression KEL.
 *
 * Le moteur empile deux portées pour une règle : une portée globale contenant
 * tous les contextes du projet, et une portée locale dont le type est le
 * contexte visé par `On`, ce qui rend ses champs accessibles sans préfixe
 * (`ScopeBuilder.doBuildScope`). Les variables déclarées dans l'expression —
 * `set`, `for`, `every`, `some` — s'empilent par-dessus et masquent le reste.
 *
 * Ces tests verrouillent cet ordre, et surtout les cas où la résolution doit
 * *échouer* : sans inférence de types, résoudre à tort serait pire que ne rien
 * résoudre du tout.
 */
class KrakenScopeResolverTest : BasePlatformTestCase() {

    private val model = """
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

    /** Premier identifiant nu portant ce texte dans le corps de la règle. */
    private fun refTo(name: String): PsiElement {
        val found = PsiTreeUtil.collectElements(myFixture.file) {
            it.node?.elementType == KrakenTypes.REF_EXPR && it.text.trim() == name
        }
        assertTrue("No reference '$name' in the file", found.isNotEmpty())
        return found.first()
    }

    private fun configureRule(body: String) = myFixture.configureByText(
        "scope.rules",
        """
        $model

        Rule "Under test" On Policy.policyCd {
            $body
        }
        """.trimIndent()
    )

    private fun resolve(name: String): PsiElement? =
        KrakenScopeResolver.resolve(refTo(name), name)

    // ------------------------------------------------------------------
    // Portée locale : les champs du contexte visé par On
    // ------------------------------------------------------------------

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
            """.trimIndent()
        )
        assertNotNull(
            "Is Insurable brings its fields into scope",
            KrakenScopeResolver.resolve(refTo("inheritedCd"), "inheritedCd")
        )
    }

    fun testUnknownNameResolvesToNothing() {
        configureRule("Assert notAField > 0")
        assertNull(resolve("notAField"))
    }

    // ------------------------------------------------------------------
    // Portée globale : les contextes par leur nom
    // ------------------------------------------------------------------

    fun testContextNameResolvesToItsDeclaration() {
        // Coverage n'est pas un enfant de Policy : le nom ne peut venir que de
        // la portée globale, qui contient tous les contextes du projet.
        configureRule("Assert Coverage != null")
        val target = resolve("Coverage")
        assertNotNull(target)
        assertEquals(KrakenTypes.CONTEXT_DECL, target!!.node.elementType)
    }

    /**
     * `Policy` déclare `Child AddressInfo` : ce nom est donc un champ du
     * contexte cible, et la portée locale du moteur passe avant la globale. Il
     * résout vers l'enfant, pas vers la déclaration du contexte.
     */
    fun testChildShadowsTheContextOfTheSameName() {
        configureRule("Assert AddressInfo != null")
        assertEquals(KrakenTypes.CHILD_DECL, resolve("AddressInfo")!!.node.elementType)
    }

    // ------------------------------------------------------------------
    // Variables de l'expression, qui masquent le reste
    // ------------------------------------------------------------------

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
     * Une variable masque un champ homonyme — c'est l'ordre du moteur. Le nom
     * déclaré après `every` n'est pas une référence, donc le seul REF_EXPR du
     * fichier est bien celui du `satisfies`.
     */
    fun testVariableShadowsAFieldOfTheSameName() {
        configureRule("Assert every limitAmount in coverages satisfies limitAmount > 0")
        val target = resolve("limitAmount")
        assertNotNull(target)
        assertFalse(
            "The quantifier variable wins over Policy.limitAmount",
            target!!.node.elementType == KrakenTypes.FIELD_DECL
        )
    }

    // ------------------------------------------------------------------
    // Chaînes d'accès
    // ------------------------------------------------------------------

    fun testContextNameDenotesItselfForTheNextSegment() {
        configureRule("Assert AddressInfo.postalCode != null")
        assertEquals("AddressInfo", KrakenScopeResolver.contextDenotedBy(refTo("AddressInfo"), "AddressInfo"))
    }

    fun testChildFieldDenotesItsContext() {
        configureRule("Assert AddressInfo.postalCode != null")
        // `Child AddressInfo` dans Policy : le nom de l'enfant est le contexte.
        assertEquals(
            "AddressInfo",
            KrakenScopeResolver.contextDenotedBy(refTo("AddressInfo"), "AddressInfo")
        )
    }

    /** Un champ scalaire ne désigne aucun contexte : la chaîne s'arrête. */
    fun testScalarFieldDenotesNoContext() {
        configureRule("Assert policyCd != null")
        assertNull(KrakenScopeResolver.contextDenotedBy(refTo("policyCd"), "policyCd"))
    }

    // ------------------------------------------------------------------
    // Complétion
    // ------------------------------------------------------------------

    fun testVisibleNamesCoverVariablesFieldsAndContexts() {
        configureRule("Assert set tmp to 1 return tmp > 0")
        val names = KrakenScopeResolver.visibleNames(refTo("tmp"))
        assertTrue("variable", names.contains("tmp"))
        assertTrue("champ du contexte cible", names.contains("policyCd"))
        assertTrue("contexte visible", names.contains("AddressInfo"))
    }
}
