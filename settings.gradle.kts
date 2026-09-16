plugins {
    // Provisions a JDK 17 when none is installed.
    //
    // Broken on Gradle 9, including 1.0.0: it still references
    // `JvmVendorSpec.IBM_SEMERU`, which Gradle 9 removed, so the download fails exactly
    // when it is needed. Kept because it is harmless wherever a JDK 17 exists (CI installs
    // one with setup-java) and will work again once foojay or Gradle is fixed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "rulescribe"
