package com.kraken.plugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.psi.KrakenFunctionCall
import com.kraken.plugin.psi.KrakenFunctionDecl

/**
 * Reconnaissance des fonctions dans le corps des règles.
 *
 * Le moteur identifie une fonction par `(nom, nombre de paramètres)` — jamais
 * par les types (`kraken.el.functionregistry.FunctionHeader`). Ces tests
 * verrouillent cette sémantique côté plugin, pour les trois provenances :
 * native Java, `Function` avec corps KEL, signature `Function` sans corps.
 */
class KrakenFunctionResolutionTest : BasePlatformTestCase() {

    private inline fun <reified T : com.intellij.psi.PsiElement> allOf(): List<T> =
        PsiTreeUtil.findChildrenOfType(myFixture.file, T::class.java).toList()

    // ------------------------------------------------------------------
    // Catalogue natif
    // ------------------------------------------------------------------

    fun testCatalogueIsLoadedFromResources() {
        assertEquals("55 fonctions natives générées depuis le moteur", 55, KrakenFunctionCatalog.functions.size)
        assertEquals(9, KrakenFunctionCatalog.libraries.size)
        assertTrue(
            "Chaque fonction porte une description",
            KrakenFunctionCatalog.functions.all { !it.description.isNullOrBlank() }
        )
    }

    fun testCatalogueDistinguishesOverloadsByArity() {
        val one = KrakenFunctionCatalog.find("Round", 1)
        val two = KrakenFunctionCatalog.find("Round", 2)
        assertNotNull(one)
        assertNotNull(two)
        assertEquals("Round(Number number) : Number", one!!.signature())
        assertEquals("Round(Number number, Number scale) : Number", two!!.signature())
        assertNull("Aucune surcharge à 3 paramètres", KrakenFunctionCatalog.find("Round", 3))
    }

    fun testCatalogueKeepsEngineTypeTokens() {
        // Types portés par @ParameterType/@ReturnType, donc non déduits du Java.
        assertEquals("Date | DateTime", KrakenFunctionCatalog.find("GetDay", 1)!!.parameters[0].type)
        assertEquals("<T>[]", KrakenFunctionCatalog.find("Distinct", 1)!!.returnType)
        assertEquals("Number[]", KrakenFunctionCatalog.find("Sum", 1)!!.parameters[0].type)
    }

    // ------------------------------------------------------------------
    // PSI
    // ------------------------------------------------------------------

    fun testFunctionDeclarationExposesNameArityAndReturnType() {
        myFixture.configureByText(
            "functions.rules",
            """
            Function Limits(Coverage[] coverages) : Number[] {
                coverages.limitAmount
            }
            """.trimIndent()
        )

        val declaration = allOf<KrakenFunctionDecl>().single()
        assertEquals("Limits", declaration.name)
        assertEquals(1, declaration.arity)
        assertEquals("Number[]", declaration.returnType)
        assertTrue(declaration.hasBody())
        assertEquals("Limits(Coverage[] coverages) : Number[]", declaration.signature())
    }

    /** Signature nue : l'implémentation vit côté Java, il n'y a pas de corps. */
    fun testSignatureWithoutBodyIsRecognised() {
        myFixture.configureByText(
            "signature.rules",
            """
            Function GetPolicyCd(Policy) : String
            """.trimIndent()
        )

        val declaration = allOf<KrakenFunctionDecl>().single()
        assertEquals("GetPolicyCd", declaration.name)
        assertEquals(1, declaration.arity)
        assertFalse("Une signature n'a pas de corps", declaration.hasBody())
    }

    fun testGenericBoundsAreNotMistakenForTheName() {
        myFixture.configureByText(
            "generic.rules",
            """
            Function <T is Coverage> First(T[] items) : T {
                items[0]
            }
            """.trimIndent()
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
            """.trimIndent()
        )

        val calls = allOf<KrakenFunctionCall>().associateBy { it.functionName }
        assertEquals(setOf("Round", "Sum"), calls.keys)
        assertEquals(2, calls["Round"]!!.argumentCount)
        assertEquals(1, calls["Sum"]!!.argumentCount)
        assertTrue("Round et Sum sont des natives", calls.values.all { it.isResolvable() })
    }

    fun testCallWithoutArgumentsHasZeroArity() {
        myFixture.configureByText(
            "today.rules",
            """
            Rule "Past date" On Policy.effectiveDate {
                Assert effectiveDate < Today()
            }
            """.trimIndent()
        )

        val call = allOf<KrakenFunctionCall>().single()
        assertEquals("Today", call.functionName)
        assertEquals(0, call.argumentCount)
        assertTrue(call.isResolvable())
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

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
            """.trimIndent()
        )

        val target = allOf<KrakenFunctionCall>().single().reference?.resolve()
        assertSame(allOf<KrakenFunctionDecl>().single(), target)
    }

    /** L'arité fait partie de l'identité : un appel mal arité ne résout pas. */
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
            """.trimIndent()
        )

        val call = allOf<KrakenFunctionCall>().single()
        assertNull(call.reference?.resolve())
        assertFalse("Ni native, ni déclarée avec cette arité", call.isResolvable())
    }

    fun testDeclaredFunctionIsNotVisibleFromAnotherNamespace() {
        myFixture.addFileToProject(
            "library.rules",
            """
            Namespace Library

            Function Hidden(Policy p) : String {
                p.policyCd
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.rules",
            """
            Namespace Consumer

            Rule "Cannot see it" On Policy.state {
                Assert Hidden(policy) != null
            }
            """.trimIndent()
        )

        val call = allOf<KrakenFunctionCall>().single()
        assertNull("Consumer n'inclut pas Library", call.reference?.resolve())
        assertFalse(call.isResolvable())
    }

    /**
     * `KrakenReferencesSearcher` ne traite que les règles et les EntryPoints,
     * dont les noms vivent dans des chaînes que le scanner de mots n'indexe
     * pas. Un nom de fonction est un identifiant ordinaire : la recherche par
     * défaut le trouve, puis filtre par `resolve()` — qui est déjà conscient
     * des namespaces. Ce test vérifie que ce chemin-là suffit, puisque c'est
     * lui qui alimente la popup d'usages et l'inlay « N usages ».
     */
    fun testFunctionUsagesAreFoundWithoutADedicatedSearcher() {
        myFixture.addFileToProject(
            "invisible.rules",
            """
            Namespace Elsewhere

            Rule "Calls a homonym" On Policy.state {
                Assert Plan(details) != null
            }
            """.trimIndent()
        )
        // Un namespace explicite est nécessaire : un fichier qui n'en déclare
        // aucun est visible depuis partout, et l'appel « Elsewhere » compterait
        // alors légitimement.
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
            """.trimIndent()
        )

        val declaration = allOf<KrakenFunctionDecl>().single()
        val usages = myFixture.findUsages(declaration)
        assertEquals("L'appel du namespace Elsewhere ne compte pas", 2, usages.size)
        assertTrue(usages.all { it.file?.name == "local.rules" })
        assertEquals(
            "2 usages",
            com.kraken.plugin.navigation.KrakenReferencesCodeVisionProvider()
                .getHint(declaration, file)
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
            """.trimIndent()
        )
        myFixture.configureByText(
            "consumer.rules",
            """
            Namespace Consumer

            Include Library

            Rule "Sees it" On Policy.state {
                Assert Shared(policy) != null
            }
            """.trimIndent()
        )

        val target = allOf<KrakenFunctionCall>().single().reference?.resolve()
        assertNotNull(target)
        assertEquals("library.rules", target!!.containingFile.name)
    }
}
