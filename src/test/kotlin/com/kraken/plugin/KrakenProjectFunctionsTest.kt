package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.functions.KrakenProjectFunctions
import com.kraken.plugin.inspection.KrakenUnknownFunctionInspection

/**
 * Découverte des fonctions natives déclarées par le projet — l'union de la
 * résolution SPI de [com.kraken.plugin.functions.KrakenLibraryFunctions] et
 * du repli par mot-index (voir la KDoc de
 * [com.kraken.plugin.functions.KrakenProjectFunctions] pour pourquoi les
 * deux tournent, même quand le plugin Java est présent). Sans ça, le plugin
 * signalait des centaines d'appels valides dans un projet d'entreprise
 * (issue #43).
 */
class KrakenProjectFunctionsTest : BasePlatformTestCase() {

    // Stubs minimaux de l'API Kraken : le vrai JAR n'est pas une dépendance
    // du plugin (voir KrakenLibraryFunctionsTest pour la version JAR compilé).
    private val api = """
        package kraken.el.functionregistry;
        import java.lang.annotation.*;
        @Retention(RetentionPolicy.RUNTIME)
        public @interface ExpressionFunction { String value(); }
    """.trimIndent()

    private val functionLibrary = "package kraken.el.functionregistry; public interface FunctionLibrary {}"

    private val library = """
        package com.acme.rules;

        import kraken.el.functionregistry.ExpressionFunction;
        import kraken.el.functionregistry.FunctionLibrary;

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

    // Pas de préfixe "src/" : la racine source du fixture léger est déjà
    // positionnée là où addFileToProject écrit — un préfixe double le
    // segment et casse la résolution par chemin relatif depuis cette racine.
    private fun addAcmeFunctions() {
        myFixture.addFileToProject("kraken/el/functionregistry/ExpressionFunction.java", api)
        myFixture.addFileToProject("kraken/el/functionregistry/FunctionLibrary.java", functionLibrary)
        myFixture.addFileToProject("com/acme/rules/AcmeFunctions.java", library)
        myFixture.addFileToProject(
            "META-INF/services/kraken.el.functionregistry.FunctionLibrary",
            "com.acme.rules.AcmeFunctions"
        )
    }

    private fun problems(body: String): List<String> {
        addAcmeFunctions()
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
        addAcmeFunctions()
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

    /**
     * Sans confirmation qu'un projet d'entreprise enregistre toujours ses
     * bibliothèques par SPI (le mécanisme documenté de l'OSS Kraken, mais pas
     * vérifié pour toute plateforme construite dessus), le repli par
     * mot-index reste actif même quand le plugin Java est présent : une
     * classe annotée sans fichier SPI est quand même trouvée, plutôt que de
     * régresser silencieusement vers « inconnue ».
     */
    fun testAnnotatedClassWithoutSpiRegistrationIsStillDiscoveredViaFallback() {
        myFixture.addFileToProject("kraken/el/functionregistry/ExpressionFunction.java", api)
        myFixture.addFileToProject("kraken/el/functionregistry/FunctionLibrary.java", functionLibrary)
        myFixture.addFileToProject("com/acme/rules/AcmeFunctions.java", library)
        // Pas de META-INF/services ici, volontairement.
        assertTrue(KrakenProjectFunctions.names(project).contains("ResolveTerritory"))
    }

    /** Un projet sans source Java ne coûte rien et ne découvre rien. */
    fun testEmptyProjectDiscoversNothing() {
        assertEquals(emptySet<String>(), KrakenProjectFunctions.names(project))
    }
}
