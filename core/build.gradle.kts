import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
}

// core relies only on the committed Paper API jar in run/libraries. Keeping the build
// offline avoids needing to resolve old/current PaperMC snapshot artifacts in CI.
dependencies {
    compileOnly(files("../run/libraries/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-1.21.11-R0.1-SNAPSHOT.jar"))
}

val coreOutput = extensions
    .getByType(SourceSetContainer::class.java)
    .getByName("main")
    .output

// Let the 1.21.11 jar include the core classes without requiring a shadow plugin.
tasks.jar {
    from(coreOutput)
}
