plugins {
    java
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // core only uses common Bukkit/Paper APIs. Use the newest 1.21.11 snapshot so the
    // dependency is available to CI and local builds (older 1.21.x snapshots can be
    // removed over time from PaperMC's snapshot repository).
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}