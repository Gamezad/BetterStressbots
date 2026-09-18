import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
}

// Offline 1.21.11 build: compile against the Paper 1.21.11 server jar and its bundled
// libraries already present in run/. No paperweight/reobf is needed because Paper
// 1.21.11 is Mojang-mapped at runtime.
dependencies {
    implementation(project(":core"))
    // Compile against the Paper 1.21.11 server jar (CraftBukkit + NMS, Mojang-mapped)
    // plus the committed runtime library jars in run/.
    //
    // The server jar contains ONLY the org.bukkit.craftbukkit implementation classes;
    // the public API types it implements (org.bukkit.Location, Bukkit, Server, World,
    // org.bukkit.entity.*) live in the paper-api jar. javac needs BOTH: without the API
    // jar it fails with "package org.bukkit does not exist" / "class file for
    // org.bukkit.Server not found". The jars do not overlap (verified: server jar has
    // no top-level org.bukkit API classes, paper-api has no craftbukkit classes), so
    // there is no duplicate-class conflict.
    compileOnly(files("../run/versions/1.21.11/paper-1.21.11.jar"))
    compileOnly(fileTree("../run/libraries") {
        include("**/*.jar")
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
