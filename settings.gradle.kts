plugins {
    // Auto-provisionne un JDK 17 si absent de la machine.
    //
    // 1.0.0 et pas 0.10.0 : cette dernière référence `JvmVendorSpec.IBM_SEMERU`,
    // que Gradle 9 a supprimé, et échoue donc au moment précis où elle sert —
    // quand un JDK 17 doit être téléchargé. Les runners CI installent Temurin 17
    // eux-mêmes, la résolution n'est jamais sollicitée et la panne reste
    // invisible ; le conteneur Qodana, lui, n'a qu'un JDK 25 et l'a révélée.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "rulescribe"
