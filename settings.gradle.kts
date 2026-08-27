pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "BetterStresstestbots"

// 26.x modules require JDK 25 / JDK 26. On older JDKs (e.g. the JDK 21 CI runner)
// they are left out so `./gradlew build` still builds the 1.21 jars.
val javaSpecification = System.getProperty("java.specification.version") ?: "17"
val javaMajor = javaSpecification.substringBefore(".").toIntOrNull() ?: 17

include("core", "v1_21", "v1_21_11")
if (javaMajor >= 25) include("v26.1.x")
if (javaMajor >= 26) include("v26.2")