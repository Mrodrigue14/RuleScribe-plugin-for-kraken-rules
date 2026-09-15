package com.kraken.plugin

import com.kraken.plugin.lang.KrakenFileType
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.jar.JarFile

/**
 * Verifies that the plugin cannot exfiltrate data: its own classes reference no network
 * API.
 *
 * This is a static property of the shipped bytecode, not "no network call happened
 * during this run". Adding an HTTP client, telemetry or a phone-home breaks this test
 * before it reaches a release.
 */
class KrakenNoNetworkEgressTest {

    // APIs that open network connections. java/net/URL and java/net/URI are allowed: they
    // serve classpath resources and open nothing by themselves.
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
            "No plugin class found (roots: $roots), so the test scans nothing.",
            ourClasses.isNotEmpty(),
        )

        val violations = mutableListOf<String>()
        for ((name, bytes) in ourClasses) {
            // ISO-8859-1 maps bytes 1:1, so constant pool class references (UTF-8, '/' separators)
            // can be found with a substring search.
            val text = String(bytes, Charsets.ISO_8859_1)
            for (api in forbidden) {
                if (text.contains(api)) {
                    violations += "$name references $api"
                }
            }
        }

        if (violations.isNotEmpty()) {
            fail(
                "The plugin references network APIs, so the \"no network " +
                    "egress\" claim no longer holds:\n  " +
                    violations.joinToString("\n  "),
            )
        }
    }

    /**
     * Classpath roots containing plugin classes. `getResources` works under any class
     * loader, including the IntelliJ test framework's, where `codeSource.location` is null.
     *
     * Two root forms exist: a directory of compiled classes, and the sandbox jar
     * (`jar:file:/…/rulescribe-X.Y.Z.jar!/com/kraken/plugin`), the shipped archive, which is
     * how the Gradle 2.x plugin exposes production code to tests. Keeping only `file:` roots
     * would scan nothing.
     *
     * Only shipped code is scanned. Test sources appear in several roots (`.../test`,
     * `.../instrumentTestCode`) and contain this test, whose [forbidden] literals would be
     * false positives. The filter checks the last path segment (source set or jar name), so
     * a parent folder containing "test" still passes: the jar lives under `plugins-test/`.
     */
    private fun pluginClassRoots(): List<File> {
        val pkg = "com/kraken/plugin"
        return KrakenFileType::class.java.classLoader.getResources(pkg).toList()
            .mapNotNull { url ->
                when (url.protocol) {
                    // <root>/com/kraken/plugin: go up three levels.
                    "file" -> File(url.toURI()).parentFile.parentFile.parentFile

                    "jar" -> jarOf(url)

                    else -> null
                }
            }
            .filterNot { it.name.lowercase().contains("test") }
            .distinct()
    }

    /** `jar:file:/…/x.jar!/com/kraken/plugin` → the `x.jar` file. */
    private fun jarOf(url: java.net.URL): File? = runCatching {
        File(java.net.URI(url.path.substringBefore("!/")))
    }.getOrNull()

    /** Plugin classes only (com/kraken/plugin), from a directory or a jar. */
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
