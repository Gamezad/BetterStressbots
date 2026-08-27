plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.gradleup.shadow") version "9.0.0"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.1.2.build.+")
    implementation(project(":core"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.compileJava {
    options.release.set(25)
}

tasks.shadowJar {
    archiveBaseName.set("BetterStresstestbots")
    archiveClassifier.set("26.1.x")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

/*
 * The default `build` lifecycle (used by the GitHub Actions "Java CI with Gradle"
 * workflow on a JDK 21 runner) should not try to compile 26.x, which needs JDK 25.
 * Build 26.x explicitly with `:v26.1.x:assemble` on a JDK 25 toolchain.
 */
tasks.named("build") {
    dependsOn.clear()
}