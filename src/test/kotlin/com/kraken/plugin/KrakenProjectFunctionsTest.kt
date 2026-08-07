package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.functions.KrakenProjectFunctions
import com.kraken.plugin.inspection.KrakenUnknownFunctionInspection

/**
 * Découverte des fonctions natives déclarées par le projet.
 *
 * Le moteur met en portée toute méthode `@ExpressionFunction` d'une
 * bibliothèque `@Native`, sans aucune déclaration DSL. Sans les lire, le plugin
 * signalait des centaines d'appels valides dans un projet d'entreprise
 * (issue #43).
 */
class KrakenProjectFunctionsTest : BasePlatformTestCase() {

    private val library = """
        package com.acme.rules;

        import kraken.el.functionregistry.ExpressionFunction;
        import kraken.el.functionregistry.FunctionLibrary;
        import kraken.el.functionregistry.Native;

        @Native
        public class AcmeFunctions implements FunctionLibrary {

            @ExpressionFunction("ResolveTerritory")
            public static String resolveTerritory(String postalCode) {
                return postalCode.substring(0, 3);
            }

            @ExpressionFunction( "RatingFactor" )
            public static Number ratingFactor(String plan, Number base) {
                return base;
            }
        }
    """.trimIndent()

    private fun problems(body: String): List<String> {
        myFixture.addFileToProject("src/AcmeFunctions.java", library)
        myFixture.configureByText(
            "rules.rules",
            """
            Root Context Policy {
                String policyCd
            }

            Rule "Under test" On Policy.policyCd {
                $body
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenUnknownFunctionInspection())
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("function", ignoreCase = true) }
    }

    fun testAnnotationNamesAreDiscovered() {
        myFixture.addFileToProject("src/AcmeFunctions.java", library)
        val found = KrakenProjectFunctions.names(project)
        assertTrue("ResolveTerritory", found.contains("ResolveTerritory"))
        assertTrue("espaces dans l'annotation", found.contains("RatingFactor"))
    }

    fun testProjectFunctionIsNotReportedAsUnknown() {
        assertEquals(
            emptyList<String>(),
            problems("Assert ResolveTerritory(policyCd) != null")
        )
    }

    /**
     * Sans lire la signature Java, on ne connaît pas l'arité : une fonction du
     * projet est donc acceptée quel que soit le nombre d'arguments. Prétendre
     * la vérifier produirait des faux positifs.
     */
    fun testProjectFunctionIsAcceptedAtAnyArity() {
        assertEquals(
            emptyList<String>(),
            problems("Assert ResolveTerritory(policyCd, 1, 2, 3) != null")
        )
    }

    /** La découverte ne rend pas l'inspection aveugle au reste. */
    fun testAnUnrelatedUnknownNameIsStillReported() {
        assertEquals(
            listOf("Unknown function 'NotDeclaredAnywhere'"),
            problems("Assert NotDeclaredAnywhere(policyCd) != null")
        )
    }

    /** Un projet sans source Java ne coûte rien et ne découvre rien. */
    fun testEmptyProjectDiscoversNothing() {
        assertEquals(emptySet<String>(), KrakenProjectFunctions.names(project))
    }
}
