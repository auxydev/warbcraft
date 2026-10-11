plugins {
    // Makinede Java 21 yoksa Gradle uygun JDK'yi kendisi indirir.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "warbcraft"

include("warbcraft-core")
include("warbcraft-plugin")
