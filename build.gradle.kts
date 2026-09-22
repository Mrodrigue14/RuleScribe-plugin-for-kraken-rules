import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
    id("org.owasp.dependencycheck") version "12.2.2"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
    id("org.cyclonedx.bom") version "3.4.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.kraken.plugin"
version = "1.0.0"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaCommunity("2024.1.7")

        // Without these, verifyPlugin and signPlugin fail asking for their binaries.
        pluginVerifier()
        zipSigner()

        // Required by BasePlatformTestCase and the test fixtures.
        testFramework(TestFrameworkType.Platform)
    }
}

kotlin {
    jvmToolchain(17)
}

// Declared once: the signing extension reads this path and writeCertificateChain
// writes it.
val signingCertificate = layout.buildDirectory.file("signing/certificate-chain.crt")

intellijPlatform {
    // The plugin declares no searchable options, and building them boots a full IDE to
    // produce an empty index.
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
            // No upper bound, so future IDE builds stay compatible.
            untilBuild = provider { null }
        }
    }

    // signPlugin reads CERTIFICATE_CHAIN, PRIVATE_KEY and PRIVATE_KEY_PASSWORD by itself,
    // but verifyPluginSignature needs a certificate path: given the raw chain, the signer
    // fails with "Invalid argument" (exit 64). This extension builds that command line,
    // hence the file declared here. A certificate is public material, so nothing secret
    // touches the disk.
    signing {
        certificateChainFile = signingCertificate
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    // Binary compatibility check against several IntelliJ versions. The list comes from
    // -PpluginVerifierIdeVersions="IC-x,IC-y", which CI fills with the latest release of
    // each major version; the default is the current target.
    pluginVerification {
        ides {
            val requested = (project.findProperty("pluginVerifierIdeVersions") as String?)
                ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
                .orEmpty()
                .ifEmpty { listOf("IC-2024.1.7") }
            // `IC-2024.1.7`: type first, as in the JetBrains list the workflow reads.
            requested.forEach { notation ->
                val (type, ideVersion) = notation.split("-", limit = 2)
                create(IntelliJPlatformType.fromCode(type), ideVersion)
            }
        }
    }
}

// The style lives in `.editorconfig`, which ktlint and IntelliJ both read.
// Nothing is excluded: Grammar-Kit generates Java, not Kotlin, into src/main/gen.
ktlint {
    version.set("1.8.0")
}

// CycloneDX SBOM of the shipped artifact: `runtimeClasspath`, the same scope as the
// OWASP scan. The default would list build-only dependencies (IntelliJ SDK, JUnit,
// Kotlin compiler) that are never distributed.
tasks.cyclonedxDirectBom {
    includeConfigs.set(listOf("runtimeClasspath"))
    // Describe what ships, not the build machine; build provenance is covered by the SLSA
    // attestation.
    includeBuildEnvironment.set(false)
    jsonOutput.set(layout.buildDirectory.file("reports/cyclonedx-direct/rulescribe-sbom.json"))
}

// The Grammar-Kit parser in src/main/gen is excluded: measuring it would inflate the
// rate without saying anything about hand-written code.
kover {
    reports {
        filters {
            excludes {
                packages("com.kraken.plugin.parser")
            }
        }
        // A floor against regressions, not a target: set below current coverage so a
        // legitimate PR passes while real erosion fails.
        verify {
            rule {
                minBound(75)
            }
        }
    }
}

// Known CVEs (NVD) in the dependencies that actually ship. Only `runtimeClasspath` is
// scanned: the IntelliJ platform is provided by the host IDE and patched by JetBrains,
// so scanning it would fail every build on CVEs outside our control.
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JUNIT")
    scanConfigurations = listOf("runtimeClasspath")

    // OWASP "single updater + readers": one job updates the NVD database and archives it;
    // reader jobs (publish) restore it and scan with -PodcAutoUpdate=false, without calling
    // NVD.
    autoUpdate = (project.findProperty("odcAutoUpdate") as String?)
        ?.toBooleanStrictOrNull() ?: true

    // The NVD API key speeds up syncing but is optional: when the secret is missing or
    // blank, dependency-check runs without it (slower) instead of failing the build.
    System.getenv("NVD_API_KEY")?.takeIf { it.isNotBlank() }?.let { key ->
        nvd {
            apiKey = key
        }
    }
}

sourceSets["main"].java.srcDirs("src/main/gen")

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
        // Lenient locally, strict in CI (-PwarningsAsErrors=true).
        kotlinOptions.allWarningsAsErrors =
            (project.findProperty("warningsAsErrors") as String?)?.toBooleanStrictOrNull() ?: false
    }
    compileJava {
        dependsOn(generateKrakenParser)
    }
    // Plugin signing lets the IDE check that the artifact comes from us unaltered. The
    // certificate chain, key and password come from CI secrets; without them (local build,
    // fork) signing is skipped instead of failing, and verifyPluginSignature with it.
    // writeCertificateChain writes the certificate the extension declares before signing
    // and verification read it.
    val writeCertificateChain = register("writeCertificateChain") {
        val chain = System.getenv("CERTIFICATE_CHAIN")
        onlyIf { !chain.isNullOrBlank() }
        outputs.file(signingCertificate)
        doLast {
            signingCertificate.get().asFile.apply {
                parentFile.mkdirs()
                writeText(chain.orEmpty())
            }
        }
    }
    signPlugin {
        onlyIf { !System.getenv("CERTIFICATE_CHAIN").isNullOrBlank() && !System.getenv("PRIVATE_KEY").isNullOrBlank() }
        // The extension's certificate applies to both tasks, and Gradle 9 requires the
        // dependency to be declared.
        dependsOn(writeCertificateChain)
    }
    verifyPluginSignature {
        onlyIf { !System.getenv("CERTIFICATE_CHAIN").isNullOrBlank() }
        // This task reads the signed archive, which the plugin does not declare as an input.
        // Gradle 9 fails on that, but only when signing actually runs (tag builds with secrets),
        // so the missing dependency is invisible otherwise.
        dependsOn(signPlugin, writeCertificateChain)
    }
}
