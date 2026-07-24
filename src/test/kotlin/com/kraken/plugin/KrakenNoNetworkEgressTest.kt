package com.kraken.plugin

import com.kraken.plugin.lang.KrakenFileType
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.jar.JarFile

/**
 * Garantit — de façon vérifiable, pas déclarative — que le plugin ne peut pas
 * exfiltrer de données : ses propres classes ne référencent AUCUNE API réseau.
 *
 * Ce n'est pas « il n'a pas appelé le réseau pendant ce test » (un état de run,
 * fragile) mais « le code compilé livré ne contient pas la moindre référence à
 * une API d'ouverture de connexion » (une propriété statique du bytecode). Une
 * régression — quelqu'un ajoute un client HTTP, de la télémétrie, un
 * phone-home — casse ce test avant d'atteindre une release.
 *
 * Argument direct pour un adoptant sensible aux données (p. ex. une institution
 * financière) : aucun egress réseau possible, contrôlé à chaque build.
 */
class KrakenNoNetworkEgressTest {

    // APIs d'INITIATION de connexion réseau. On ne bannit pas java/net/URL ni
    // java/net/URI : ils servent aux ressources du classpath (getResource) et
    // n'ouvrent rien par eux-mêmes. On cible ce qui envoie effectivement des
    // octets sur le réseau, y compris les clients HTTP tiers courants.
    private val forbidden = listOf(
        "java/net/Socket",
        "java/net/ServerSocket",
        "java/net/DatagramSocket",
        "java/net/MulticastSocket",
        "java/net/http/HttpClient",
        "java/net/URLConnection",
        "java/net/HttpURLConnection",
        "javax/net/ssl/SSLSocket",
        "javax/net/SocketFactory",
        "okhttp3/",
        "org/apache/http",
        "org/apache/hc/",
        "retrofit2/",
        "io/ktor/",
    )

    @Test
    fun `plugin classes reference no network API`() {
        val roots = pluginClassRoots()
        val ourClasses = roots.flatMap { collectClassBytes(it) }
        assertTrue(
            "Aucune classe du plugin trouvée (racines : $roots) — le test ne scanne rien.",
            ourClasses.isNotEmpty(),
        )

        val violations = mutableListOf<String>()
        for ((name, bytes) in ourClasses) {
            // ISO-8859-1 préserve chaque octet 1:1 : les références de classes
            // du pool de constantes (UTF-8, séparateur '/') sont retrouvables
            // par simple recherche de sous-chaîne.
            val text = String(bytes, Charsets.ISO_8859_1)
            for (api in forbidden) {
                if (text.contains(api)) {
                    violations += "$name référence $api"
                }
            }
        }

        if (violations.isNotEmpty()) {
            fail(
                "Le plugin référence des API réseau — l'affirmation « aucun " +
                    "egress réseau » ne tient plus :\n  " +
                    violations.joinToString("\n  "),
            )
        }
    }

    /**
     * Racines du classpath contenant nos classes. Le chargement de ressource
     * (getResources) fonctionne sous n'importe quel classloader — y compris
     * celui du framework de test IntelliJ, où `codeSource.location` est null.
     * Kotlin (kotlin/main) et parseur généré (java/main) vivent dans des
     * racines distinctes : on les récupère toutes.
     */
    private fun pluginClassRoots(): List<File> {
        val pkg = "com/kraken/plugin"
        return KrakenFileType::class.java.classLoader.getResources(pkg).toList()
            .filter { it.protocol == "file" }
            // <racine>/com/kraken/plugin → remonter de 3 niveaux vers <racine>.
            .map { File(it.toURI()).parentFile.parentFile.parentFile }
            // On ne scanne QUE le code livré. Les sources de TEST apparaissent
            // dans plusieurs racines (`.../test`, mais aussi le
            // `.../instrumentTestCode` produit par le plugin IntelliJ), et
            // contiennent ce test lui-même : ses littéraux de `forbidden`
            // seraient un faux positif. On filtre sur le dernier segment (le nom
            // du source-set) pour rester robuste si un dossier parent contient
            // « test » (p. ex. un utilisateur nommé « tester »).
            .filterNot { it.name.lowercase().contains("test") }
            .distinct()
    }

    /** Nos classes uniquement (com/kraken/plugin), depuis un répertoire ou un jar. */
    private fun collectClassBytes(root: File): List<Pair<String, ByteArray>> {
        val prefix = "com/kraken/plugin/"
        if (root.isDirectory) {
            return root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }
                .filter { it.first.startsWith(prefix) }
                .toList()
        }
        JarFile(root).use { jar ->
            return jar.entries().asSequence()
                .filter { it.name.startsWith(prefix) && it.name.endsWith(".class") }
                .map { entry -> entry.name to jar.getInputStream(entry).readBytes() }
                .toList()
        }
    }
}
