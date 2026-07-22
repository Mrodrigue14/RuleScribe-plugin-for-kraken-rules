import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
    id("org.owasp.dependencycheck") version "12.2.2"
}

group = "com.kraken.plugin"
version = "0.5.7"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// Plateforme IntelliJ cible (IntelliJ IDEA Community 2024.1)
intellij {
    version.set("2024.1.7")
    type.set("IC")
}

kotlin {
    jvmToolchain(17)
}

// Analyse des CVE connues dans les dépendances réellement livrées (base NVD).
//
// On ne scanne que `runtimeClasspath` — c'est-à-dire ce que le plugin embarque
// dans son zip. La plateforme IntelliJ (netty, commons-lang3, httpcore… tirés
// par le plugin org.jetbrains.intellij) n'est PAS livrée : elle est fournie par
// l'IDE hôte à l'exécution et corrigée par JetBrains via les mises à jour de
// l'IDE. La scanner ferait échouer chaque build sur des CVE hors de notre
// contrôle. Scoper à runtimeClasspath garde la porte CVSS>=7 pertinente pour
// toute vraie dépendance embarquée qu'on ajouterait à l'avenir.
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JUNIT")
    scanConfigurations = listOf("runtimeClasspath")

    // La clé API NVD (secret CI) accélère la synchro de la base. Elle reste
    // facultative : si le secret est absent ou vide (p. ex. clé expirée puis
    // retirée), on ne la passe pas et dependency-check bascule sur le mode
    // sans clé (fonctionnel, juste plus lent) au lieu de casser le build.
    System.getenv("NVD_API_KEY")?.takeIf { it.isNotBlank() }?.let { key ->
        nvd {
            apiKey = key
        }
    }
}

// Les sources générées par Grammar-Kit sont compilées avec le reste
sourceSets["main"].java.srcDirs("src/main/gen")

// Génération du parser à partir de la grammaire BNF
val generateKrakenParser = tasks.register<GenerateParserTask>("generateKrakenParser") {
    sourceFile.set(file("src/main/bnf/Kraken.bnf"))
    targetRootOutputDir.set(file("src/main/gen"))
    pathToParser.set("com/kraken/plugin/parser/KrakenParser.java")
    pathToPsiRoot.set("com/kraken/plugin/psi")
    purgeOldFiles.set(true)
}

tasks {
    withType<KotlinCompile> {
        dependsOn(generateKrakenParser)
    }
    compileJava {
        dependsOn(generateKrakenParser)
    }
    patchPluginXml {
        sinceBuild.set("241")
        // Pas de borne supérieure : compatible avec les builds futurs (2026.1+)
        untilBuild.set(provider { null })
    }
    buildSearchableOptions {
        enabled = false
    }
    // Vérifie la compatibilité binaire du plugin contre plusieurs versions
    // d'IntelliJ (API supprimées/dépréciées) via le Plugin Verifier officiel.
    // Les versions sont passées avec -PpluginVerifierIdeVersions="IC-x,IC-y"
    // (liste séparée par des virgules) ; par défaut la version cible actuelle.
    runPluginVerifier {
        val versions = (project.findProperty("pluginVerifierIdeVersions") as String?)
            ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
            .orEmpty()
            .ifEmpty { listOf("IC-2024.1.7") }
        ideVersions.set(versions)
    }
}
