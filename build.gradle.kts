import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

group = "com.kraken.plugin"
version = "0.4.0"

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
}
