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