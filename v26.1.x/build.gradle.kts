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