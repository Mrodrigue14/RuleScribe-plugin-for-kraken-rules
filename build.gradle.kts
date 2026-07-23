import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
    id("org.owasp.dependencycheck") version "12.2.2"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "com.kraken.plugin"
version = "0.7.0"

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
// par le plugin org.jetbrains.intellij) n'est PAS livrée : elle est fournie par
// l'IDE hôte à l'exécution et corrigée par JetBrains via les mises à jour de
// l'IDE. La scanner ferait échouer chaque build sur des CVE hors de notre
// contrôle. Scoper à runtimeClasspath garde la porte CVSS>=7 pertinente pour
// toute vraie dépendance embarquée qu'on ajouterait à l'avenir.
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
    // La liste des versions est fournie via -PpluginVerifierIdeVersions="IC-x,IC-y".
    // Le workflow CI l'alimente dynamiquement depuis l'API JetBrains (dernière
    // release + EAP) pour rester à jour automatiquement ; par défaut, la
    // version cible actuelle.
    runPluginVerifier {
        val versions = (project.findProperty("pluginVerifierIdeVersions") as String?)
            ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
            .orEmpty()
            .ifEmpty { listOf("IC-2024.1.7") }
        ideVersions.set(versions)
    }
    // Signature cryptographique du plugin : l'IDE peut vérifier que l'artefact
    // vient bien de nous et n'a pas été altéré. Complète l'attestation SLSA
    // (qui prouve « buildé par la pipeline ») côté distribution.
    //
    // Les trois éléments viennent de secrets CI, jamais du dépôt. Si aucun
    // n'est fourni (build local, fork), la tâche est simplement sautée et la
    // publication se fait sans signature — on ne casse pas le build.
    signPlugin {
        val chain = System.getenv("CERTIFICATE_CHAIN")
        val key = System.getenv("PRIVATE_KEY")
        val pwd = System.getenv("PRIVATE_KEY_PASSWORD")
        onlyIf { !chain.isNullOrBlank() && !key.isNullOrBlank() }
        if (!chain.isNullOrBlank()) certificateChain.set(chain)
        if (!key.isNullOrBlank()) privateKey.set(key)
        if (!pwd.isNullOrBlank()) password.set(pwd)
    }
    // Contre-vérification : relit l'archive signée et valide la signature
    // contre le certificat. Exécutée avant la publication, elle transforme une
    // clé mal formée ou un certificat dépareillé en échec net plutôt qu'en
    // artefact publié dont la signature ne vaut rien.
    // verifyPluginSignature attend un FICHIER de certificat : on le matérialise
    // via une vraie tâche productrice, pour que Gradle l'ait écrit avant de
    // valider les entrées de la vérification. Un certificat est du matériel
    // public (seule la clé privée est sensible) : rien de secret sur disque.
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
        val chain = System.getenv("CERTIFICATE_CHAIN")
        onlyIf { !chain.isNullOrBlank() }
        dependsOn(writeCertificateChain)
        certificateChainFile.set(certificateChainFilePath)
    }
    // Publication sur le JetBrains Marketplace. Le token est fourni par la
    // variable d'environnement PUBLISH_TOKEN (secret CI), jamais en clair.
    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
