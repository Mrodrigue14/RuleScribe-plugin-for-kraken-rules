package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.psi.KrakenFunctionTarget

/**
 * Function recognition in rule bodies.
 *
 * The engine identifies a function by `(name, parameter count)`, never by types
 * (`kraken.el.functionregistry.FunctionHeader`). These tests pin that for all three
 * origins: native Java, `Function` with a KEL body, bodiless `Function` signature.
 */
class KrakenFunctionResolutionTest : BasePlatformTestCase() {

    private inline fun <reified T : com.intellij.psi.PsiElement> allOf(): List<T> = PsiTreeUtil.findChildrenOfType(myFixture.file, T::class.java).toList()

    fun testCatalogueIsLoadedFromResources() {
        assertEquals("55 native functions generated from the engine", 55, KrakenFunctionCatalog.functions.size)
        assertEquals(9, KrakenFunctionCatalog.libraries.size)
        assertTrue(
            "Every function has a description",
            KrakenFunctionCatalog.functions.all { !it.description.isNullOrBlank() },
        )
    }

    fun testCatalogueDistinguishesOverloadsByArity() {
        val one = KrakenFunctionCatalog.find("Round", 1)
        val two = KrakenFunctionCatalog.find("Round", 2)
        assertNotNull(one)
        assertNotNull(two)
        assertEquals("Round(Number number) : Number", one!!.signature())
        assertEquals("Round(Number number, Number scale) : Number", two!!.signature())
        assertNull("No overload with 3 parameters", KrakenFunctionCatalog.find("Round", 3))
    }

    fun testCatalogueKeepsEngineTypeTokens() {
        // Types come from @ParameterType/@ReturnType, not inferred from the Java signature.
        assertEquals("Date | DateTime", KrakenFunctionCatalog.find("GetDay", 1)!!.parameters[0].type)
        assertEquals("<T>[]", KrakenFunctionCatalog.find("Distinct", 1)!!.returnType)
        assertEquals("Number[]", KrakenFunctionCatalog.find("Sum", 1)!!.parameters[0].type)
    }

    fun testFunctionDeclarationExposesNameArityAndReturnType() {
        myFixture.configureByText(
            "functions.rules",
            """
            Function Limits(Coverage[] coverages) : Number[] {
                coverages.limitAmount
            }
            """.trimIndent(),
        )

        val declaration = allOf<KrakenFunctionDecl>().single()
        assertEquals("Limits", declaration.name)
        assertEquals(1, declaration.arity)
        assertEquals("Number[]", declaration.returnType)
        assertTrue(declaration.hasBody())
        assertEquals("Limits(Coverage[] coverages) : Number[]", declaration.signature())
    }

    fun testSignatureWithoutBodyIsRecognised() {
        myFixture.configureByText(
            "signature.rules",
            """
            Function GetPolicyCd(Policy) : String
            """.trimIndent(),
        )

        val declaration = allOf<KrakenFunctionDecl>().single()
        assertEquals("GetPolicyCd", declaration.name)
        assertEquals(1, declaration.arity)
        assertFalse("A signature has no body", declaration.hasBody())
    }

    fun testGenericBoundsAreNotMistakenForTheName() {
        myFixture.configureByText(
            "generic.rules",
            """
            Function <T is Coverage> First(T[] items) : T {
                items[0]
            }
            """.trimIndent(),
        )

        assertEquals("First", allOf<KrakenFunctionDecl>().single().name)
    }

    fun testCallExposesNameAndArgumentCount() {
        myFixture.configureByText(
            "call.rules",
            """
            Rule "Uses functions" On Policy.limit {
                Assert Round(Sum(coverages.limitAmount), 2) > 0
            }
            """.trimIndent(),
        )

        val calls = allOf<KrakenFunctionCall>().associateBy { it.functionName }
        assertEquals(setOf("Round", "Sum"), calls.keys)
        assertEquals(2, calls["Round"]!!.argumentCount)
        assertEquals(1, calls["Sum"]!!.argumentCount)
        assertTrue("Round and Sum are natives", calls.values.all { it.target() is KrakenFunctionTarget.Native })
    }

    fun testCallWithoutArgumentsHasZeroArity() {
        myFixture.configureByText(
            "today.rules",
            """
            Rule "Past date" On Policy.effectiveDate {
                Assert effectiveDate < Today()
            }
            """.trimIndent(),
        )

        val call = allOf<KrakenFunctionCall>().single()
        assertEquals("Today", call.functionName)
        assertEquals(0, call.argumentCount)
        assertNotNull(call.target())
    }

    fun testCallResolvesToDeclaredFunction() {
        myFixture.configureByText(
            "local.rules",
            """
            Function Plan(PackageDetails details) : String {
                details.planCd
            }

            Rule "Uses plan" On Policy.state {
                Assert Plan(packageDetails) != null
            }
            """.trimIndent(),
        )

        val target = allOf<KrakenFunctionCall>().single().reference?.resolve()
        assertSame(allOf<KrakenFunctionDecl>().single(), target)
    }

    /** The engine overrides natives with declared functions of the same name and arity. */
    fun testDeclaredSignatureShadowsANative() {
        myFixture.configureByText(
            "shadow.rules",
            """
            Function Round(Number n) : String

            Rule "Rounds" On Policy.state {
                Assert Round(1) != null
            }
            """.trimIndent(),
        )

        val target = allOf<KrakenFunctionCall>().single().target()
        assertEquals(KrakenFunctionTarget.Declared(allOf<KrakenFunctionDecl>().single()), target)
        assertEquals("String", target!!.returnType)
    }

    /** A bodiless signature overrides a `Function` with a body, as in the engine. */
    fun testSignatureWinsOverAFunctionWithABody() {
        myFixture.configureByText(
            "both.rules",
            """
            Function Plan(PackageDetails details) : String {
                details.planCd
            }

            Function Plan(PackageDetails details) : Number

            Rule "Uses plan" On Policy.state {
                Assert Plan(packageDetails) != null
            }
            """.trimIndent(),
        )

        val target = allOf<KrakenFunctionCall>().single().reference?.resolve() as KrakenFunctionDecl
        assertFalse(target.hasBody())
    }

    /** Arity is part of the identity: a call with the wrong arity does not resolve. */
    fun testCallWithWrongArityDoesNotResolve() {
        myFixture.configureByText(
            "arity.rules",
            """
            Function Plan(PackageDetails details) : String {
                details.planCd
            }

            Rule "Wrong arity" On Policy.state {
                Assert Plan(a, b) != null
            }
            """.trimIndent(),
        )

        val call = allOf<KrakenFunctionCall>().single()
        assertNull(call.reference?.resolve())
        assertNull("Neither native nor declared with this arity", call.target())
    }

    fun testDeclaredFunctionIsNotVisibleFromAnotherNamespace() {
        myFixture.addFileToProject(
            "library.rules",
            """
            Namespace Library

            Function Hidden(Policy p) : String {
                p.policyCd
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "consumer.rules",
            """
            Namespace Consumer

            Rule "Cannot see it" On Policy.state {
                Assert Hidden(policy) != null
            }
            """.trimIndent(),
        )

        val call = allOf<KrakenFunctionCall>().single()
        assertNull("Consumer does not include Library", call.reference?.resolve())
        assertNull(call.target())
    }

    /**
     * `KrakenReferencesSearcher` only handles rules and EntryPoints, whose names live in
     * strings the word scanner does not index. A function name is a plain identifier: the
     * default search finds it and filters with `resolve()`, which already knows about
     * namespaces. This checks that this path is enough, since it feeds the usages popup and
     * the "N usages" inlay.
     */
    fun testFunctionUsagesAreFoundWithoutADedicatedSearcher() {
        myFixture.addFileToProject(
            "invisible.rules",
            """
            Namespace Elsewhere

            Rule "Calls a homonym" On Policy.state {
                Assert Plan(details) != null
            }
            """.trimIndent(),
        )
        // An explicit namespace is needed: a file without one is visible from everywhere, and
        // the "Elsewhere" call would then legitimately count.
        val file = myFixture.configureByText(
            "local.rules",
            """
            Namespace Local

            Function Plan(PackageDetails details) : String {
                details.planCd
            }

            Rule "First caller" On Policy.state {
                Assert Plan(details) != null
            }

            Rule "Second caller" On Policy.term {
                Assert Plan(other) != null
            }
            """.trimIndent(),
        )

        val declaration = allOf<KrakenFunctionDecl>().single()
        val usages = myFixture.findUsages(declaration)
        assertEquals("The call from namespace Elsewhere does not count", 2, usages.size)
        assertTrue(usages.all { it.file?.name == "local.rules" })
        assertEquals(
            "2 usages",
            com.kraken.plugin.navigation.KrakenReferencesCodeVisionProvider()
                .getHint(declaration, file),
        )
    }

    fun testDeclaredFunctionIsVisibleThroughInclude() {
        myFixture.addFileToProject(
            "library.rules",
            """
            Namespace Library

            Function Shared(Policy p) : String {
                p.policyCd
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "consumer.rules",
            """
            Namespace Consumer

            Include Library

            Rule "Sees it" On Policy.state {
                Assert Shared(policy) != null
            }
            """.trimIndent(),
        )

        val target = allOf<KrakenFunctionCall>().single().reference?.resolve()
        assertNotNull(target)
        assertEquals("library.rules", target!!.containingFile.name)
    }
}
