plugins {
    // Auto-provisionne un JDK 17 si absent de la machine.
    //
    // Cassé sous Gradle 9, et 1.0.0 ne le répare pas : `DistributionsKt` y
    // nomme encore `JvmVendorSpec.IBM_SEMERU`, que Gradle 9 a supprimé (il
    // déclare ADOPTIUM, AMAZON, APPLE, AZUL, BELLSOFT, GRAAL_VM, IBM, ORACLE,
    // SAP). Le téléchargement échoue donc au moment précis où il sert, et
    // aucune version publiée n'y change quelque chose aujourd'hui.
    //
    // Gardé quand même : inoffensif dès qu'un JDK 17 est présent — ce qui est
    // le cas partout où le projet se construit, les runners CI l'installant
    // via setup-java — et la provision repartira sans rien toucher le jour où
    // foojay ou Gradle se raccordent. Version courante plutôt qu'une plus
    // ancienne aussi cassée.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "rulescribe"
