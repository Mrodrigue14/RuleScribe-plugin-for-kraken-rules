import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
    id("org.owasp.dependencycheck") version "12.2.2"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
    id("org.cyclonedx.bom") version "3.4.1"
}

group = "com.kraken.plugin"
version = "0.16.0"

repositories {
    mavenCentral()

    // Dépôts d'où sortent la plateforme elle-même et son outillage (Plugin
    // Verifier, ZIP Signer). En 1.x le plugin les déclarait en douce ; 2.x
    // demande qu'ils soient écrits, ce qui rend visible d'où vient le SDK.
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // Plateforme IntelliJ cible (IntelliJ IDEA Community 2024.1). C'était le
    // bloc `intellij { version; type }` en 1.x : une dépendance déclarée
    // plutôt qu'une extension, donc résolue comme n'importe quelle autre.
    intellijPlatform {
        intellijIdeaCommunity("2024.1.7")

        // Outils tirés à la demande, au lieu d'être embarqués dans le plugin
        // Gradle comme en 1.x. Sans eux, `verifyPlugin` et `signPlugin`
        // échouent en réclamant leur binaire.
        pluginVerifier()
        zipSigner()

        // `BasePlatformTestCase` et les fixtures dont dépend toute la suite.
        // La 1.x les mettait au classpath de test d'office ; 2.x veut la
        // dépendance écrite, sinon le code de test ne compile plus.
        testFramework(TestFrameworkType.Platform)
    }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    // Les options recherchables sont une page de réglages indexée au build.
    // Le plugin n'en déclare aucune : les générer coûte un démarrage d'IDE
    // complet pour produire un index vide.
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
            // Pas de borne supérieure : compatible avec les builds futurs (2026.1+)
            untilBuild = provider { null }
        }
    }

    // Publication sur le JetBrains Marketplace. Le token est fourni par la
    // variable d'environnement PUBLISH_TOKEN (secret CI), jamais en clair.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    // Vérifie la compatibilité binaire du plugin contre plusieurs versions
    // d'IntelliJ (API supprimées/dépréciées) via le Plugin Verifier officiel.
    // La liste des versions est fournie via -PpluginVerifierIdeVersions="IC-x,IC-y".
    // Le workflow CI l'alimente dynamiquement depuis l'API JetBrains (dernière
    // release de chaque majeure) pour rester à jour automatiquement ; par
    // défaut, la version cible actuelle.
    pluginVerification {
        ides {
            val requested = (project.findProperty("pluginVerifierIdeVersions") as String?)
                ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
                .orEmpty()
                .ifEmpty { listOf("IC-2024.1.7") }
            // `IC-2024.1.7` : le type précède la version, comme dans la liste
            // publiée par JetBrains que le workflow lit.
            requested.forEach { notation ->
                val (type, ideVersion) = notation.split("-", limit = 2)
                create(IntelliJPlatformType.fromCode(type), ideVersion)
            }
        }
    }
}

// SBOM CycloneDX de l'artefact LIVRÉ. Même périmètre que le scan OWASP :
// `runtimeClasspath`, c'est-à-dire ce que le zip embarque réellement.
//
// Sans ce cadrage, le SBOM par défaut liste 28 composants (SDK IntelliJ, JUnit,
// compilateur Kotlin, Ant…) qui sont des dépendances de BUILD, jamais
// distribuées — il contredirait frontalement le « zéro dépendance tierce » que
// le rapport OWASP démontre par ailleurs.
tasks.cyclonedxDirectBom {
    includeConfigs.set(listOf("runtimeClasspath"))
    // Le SBOM décrit ce qui est LIVRÉ, pas la machine qui a compilé : sans
    // cela, Gradle et le JDK du runner s'y retrouveraient. La provenance du
    // build est déjà couverte par l'attestation SLSA.
    includeBuildEnvironment.set(false)
    jsonOutput.set(layout.buildDirectory.file("reports/cyclonedx-direct/rulescribe-sbom.json"))
}

// Couverture de tests. Le parseur de `src/main/gen` est généré par Grammar-Kit
// à partir du BNF : le mesurer gonflerait artificiellement le taux sans rien
// dire de la qualité du code écrit à la main. Même raisonnement que le filtrage
// du SARIF CodeQL, qui exclut déjà ce répertoire.
kover {
    reports {
        filters {
            excludes {
                packages("com.kraken.plugin.parser")
            }
        }
        // Plancher anti-régression, pas objectif à atteindre. La couverture
        // réelle du code écrit à la main est ~81 % ; 75 % laisse de la marge
        // pour une PR légitime tout en signalant une érosion franche. Viser un
        // seuil collé à la valeur courante rendrait le build cassant sans rien
        // améliorer.
        verify {
            rule {
                minBound(75)
            }
        }
    }
}

// Analyse des CVE connues dans les dépendances réellement livrées (base NVD).
//
// On ne scanne que `runtimeClasspath` — c'est-à-dire ce que le plugin embarque
// dans son zip. La plateforme IntelliJ (netty, commons-lang3, httpcore… tirés
// par le plugin org.jetbrains.intellij.platform) n'est PAS livrée : elle est
// fournie par l'IDE hôte à l'exécution et corrigée par JetBrains via les mises
// à jour de l'IDE. La scanner ferait échouer chaque build sur des CVE hors de
// notre contrôle. Scoper à runtimeClasspath garde la porte CVSS>=7 pertinente
// pour toute vraie dépendance embarquée qu'on ajouterait à l'avenir.
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JUNIT")
    scanConfigurations = listOf("runtimeClasspath")

    // Stratégie "single updater + readers" recommandée par OWASP : un job
    // met à jour la base NVD (autoUpdate = true) et archive le `data` dir ;
    // les jobs "lecteurs" (ex. publish) le restaurent et scannent avec
    // -PodcAutoUpdate=false → aucun appel NVD, donc rapide et fiable.
    autoUpdate = (project.findProperty("odcAutoUpdate") as String?)
        ?.toBooleanStrictOrNull() ?: true

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
        // Souple en local, strict en CI (-PwarningsAsErrors=true) : un
        // avertissement du compilateur ne doit pas casser une itération de
        // développement, mais il ne doit pas non plus s'accumuler en silence
        // dans la branche stable.
        kotlinOptions.allWarningsAsErrors =
            (project.findProperty("warningsAsErrors") as String?)?.toBooleanStrictOrNull() ?: false
    }
    compileJava {
        dependsOn(generateKrakenParser)
    }
    // Signature cryptographique du plugin : l'IDE peut vérifier que l'artefact
    // vient bien de nous et n'a pas été altéré. Complète l'attestation SLSA
    // (qui prouve « buildé par la pipeline ») côté distribution.
    //
    // Les trois éléments viennent de secrets CI, jamais du dépôt. 2.x lit
    // CERTIFICATE_CHAIN, PRIVATE_KEY et PRIVATE_KEY_PASSWORD tout seul, ce qui
    // remplace le câblage explicite de la 1.x — et aussi la tâche
    // `writeCertificateChain`, qui n'existait que pour matérialiser le
    // certificat en fichier parce que `verifyPluginSignature` en voulait un.
    //
    // Reste ce que le plugin ne fait pas : sauter la signature quand les
    // secrets sont absents (build local, fork) plutôt que casser le build.
    // `verifyPluginSignature` suit, sinon elle relirait une archive non signée.
    signPlugin {
        onlyIf { !System.getenv("CERTIFICATE_CHAIN").isNullOrBlank() && !System.getenv("PRIVATE_KEY").isNullOrBlank() }
    }
    // `verifyPluginSignature` attend un FICHIER de certificat : passé la chaîne
    // brute, le signeur répond « Invalid argument » et sort en 64. La 2.x lit
    // pourtant CERTIFICATE_CHAIN toute seule et alimente `certificateChain`,
    // la forme chaîne — d'où cette tâche productrice, qui matérialise le
    // certificat sur disque pour que Gradle l'ait écrit avant que la
    // vérification n'évalue ses entrées. Un certificat est du matériel public
    // (seule la clé privée est sensible) : rien de secret ne touche le disque.
    val certificateChainFilePath = layout.buildDirectory.file("signing/certificate-chain.crt")
    val writeCertificateChain = register("writeCertificateChain") {
        val chain = System.getenv("CERTIFICATE_CHAIN")
        onlyIf { !chain.isNullOrBlank() }
        outputs.file(certificateChainFilePath)
        doLast {
            certificateChainFilePath.get().asFile.apply {
                parentFile.mkdirs()
                writeText(chain.orEmpty())
            }
        }
    }
    verifyPluginSignature {
        onlyIf { !System.getenv("CERTIFICATE_CHAIN").isNullOrBlank() }
        // La tâche relit l'archive signée sans que le plugin ne déclare d'où
        // elle vient : `gradlew buildPlugin signPlugin verifyPluginSignature`
        // ne marchait que parce que l'ordre de la ligne de commande tombait
        // juste. Gradle 9 en fait une erreur dure, et seulement quand la
        // signature a réellement lieu — donc uniquement sur un tag, avec les
        // secrets. Sans eux les deux tâches sont sautées, ne produisent rien,
        // et la dépendance manquante reste invisible.
        dependsOn(signPlugin, writeCertificateChain)
        certificateChainFile.set(certificateChainFilePath)
    }
}
