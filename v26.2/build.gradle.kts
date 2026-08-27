plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.gradleup.shadow") version "9.0.0"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.2.build.+")
    implementation(project(":core"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(26))
}

tasks.compileJava {
    options.release.set(26)
}

tasks.shadowJar {
    archiveBaseName.set("BetterStresstestbots")
    archiveClassifier.set("26.2")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

/*
 * The default `build` lifecycle (used by the GitHub Actions "Java CI with Gradle"
 * workflow on a JDK 21 runner) should not try to compile 26.2, which needs JDK 26.
 * Build 26.2 explicitly with `:v26.2:assemble` on a JDK 26 toolchain.
 */
tasks.named("build") {
    dependsOn.clear()
}