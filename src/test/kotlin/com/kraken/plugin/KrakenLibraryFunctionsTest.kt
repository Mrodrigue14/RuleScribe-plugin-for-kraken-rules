package com.kraken.plugin

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.functions.KrakenProjectFunctions
import com.kraken.plugin.inspection.KrakenUnknownFunctionInspection
import java.io.File

/**
 * Fonctions `@ExpressionFunction` livrées en `.class` dans un JAR Maven —
 * jamais présentes en `.java` dans le projet. C'est le cas signalé par un
 * utilisateur en production : une bibliothèque `FunctionLibrary` interne, vue
 * en bytecode décompilé, faisait signaler ~300 appels valides comme inconnus.
 *
 * `library-functions.jar` (testData/functions) est un fixture précompilé qui
 * contient, comme un vrai JAR Kraken, une entrée
 * `META-INF/services/kraken.el.functionregistry.FunctionLibrary` listant
 * `com.acme.lib.LibraryFunctions` (`@ExpressionFunction("IsTrue")`) — c'est
 * ce fichier SPI, et non l'annotation elle-même, que la découverte cherche
 * maintenant en premier. Précompilé pour ne pas dépendre d'un compilateur
 * Java disponible au moment du test.
 */
class KrakenLibraryFunctionsTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    fun testAnnotationInCompiledLibraryJarIsDiscovered() {
        val jar = File(testDataPath, "functions/library-functions.jar").absoluteFile
        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, jar.parent)
        // Overload liée à un Disposable : la bibliothèque est retirée en fin de
        // test, sinon elle persiste sur le projet léger réutilisé par les tests
        // suivants (LightPlatformTestCase le met en cache entre méthodes).
        PsiTestUtil.addLibrary(myFixture.testRootDisposable, module, "kraken-lib", jar.parent, jar.name)

        val found = KrakenProjectFunctions.names(project)
        assertTrue("IsTrue absent d'un JAR compilé", found.contains("IsTrue"))

        myFixture.configureByText(
            "rules.rules",
            """
            Root Context Policy {
                String policyCd
            }

            Rule "Under test" On Policy.policyCd {
                Assert IsTrue(policyCd) = true
            }
            """.trimIndent()
        )
        myFixture.enableInspections(KrakenUnknownFunctionInspection())
        val problems = myFixture.doHighlighting().mapNotNull { it.description }
            .filter { it.contains("function", ignoreCase = true) }
        assertEquals(emptyList<String>(), problems)
    }
}
