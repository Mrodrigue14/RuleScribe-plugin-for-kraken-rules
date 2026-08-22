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
     *
     * Deux formes de racine coexistent. Un répertoire, pour les classes
     * compilées d'un source-set. Et le **jar du sandbox**
     * (`jar:file:/…/rulescribe-X.Y.Z.jar!/com/kraken/plugin`), qui est
     * l'archive réellement livrée : c'est sous cette forme que le plugin
     * Gradle 2.x présente le code de production au classpath de test. Ne
     * garder que `file:` reviendrait à ne rien scanner du tout.
     *
     * On ne scanne QUE le code livré. Les sources de TEST apparaissent dans
     * plusieurs racines (`.../test`, et aussi le `.../instrumentTestCode`
     * produit par le plugin IntelliJ), et contiennent ce test lui-même : les
     * littéraux de [forbidden] y seraient un faux positif. Le filtre porte sur
     * le dernier segment, donc sur le nom du source-set ou du jar, ce qui
     * laisse passer un chemin dont un dossier parent contient « test » — le
     * jar vit sous `plugins-test/`, et un utilisateur peut s'appeler
     * « tester ».
     */
    private fun pluginClassRoots(): List<File> {
        val pkg = "com/kraken/plugin"
        return KrakenFileType::class.java.classLoader.getResources(pkg).toList()
            .mapNotNull { url ->
                when (url.protocol) {
                    // <racine>/com/kraken/plugin → remonter de 3 niveaux.
                    "file" -> File(url.toURI()).parentFile.parentFile.parentFile
                    "jar" -> jarOf(url)
                    else -> null
                }
            }
            .filterNot { it.name.lowercase().contains("test") }
            .distinct()
    }

    /** `jar:file:/…/x.jar!/com/kraken/plugin` → le fichier `x.jar`. */
    private fun jarOf(url: java.net.URL): File? = runCatching {
        File(java.net.URI(url.path.substringBefore("!/")))
    }.getOrNull()

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
