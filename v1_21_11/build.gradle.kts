import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
}

// Offline 1.21.11 build: compile against the Paper 1.21.11 server jar and its bundled
// libraries already present in run/. No paperweight/reobf is needed because Paper
// 1.21.11 is Mojang-mapped at runtime.
dependencies {
    implementation(project(":core"))
    compileOnly(files("../run/versions/1.21.11/paper-1.21.11.jar"))
    // The Paper server jar already contains the full Bukkit/CraftBukkit API and seals
    // org.bukkit. Exclude the duplicate paper-api jar so javac does not reject the
    // sealed-package conflict. Library jars live in nested version directories, so the
    // recursive `**` pattern is required here.
    compileOnly(fileTree("../run/libraries") {
        include("**/*.jar")
        exclude("**/io/papermc/paper/paper-api/**")
    })
}

val coreOutput = project(":core")
    .extensions.getByType(SourceSetContainer::class.java)
    .getByName("main")
    .output

tasks.jar {
    archiveBaseName.set("BetterStresstestbots")
    archiveVersion.set("2.0.0")
    archiveClassifier.set("1.21.11")
    from(coreOutput)
}
