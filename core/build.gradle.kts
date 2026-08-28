plugins {
    java
}

// core relies only on the committed Paper API jar in run/libraries. Keeping the build
// offline avoids needing to resolve old/current PaperMC snapshot artifacts in CI.
dependencies {
    compileOnly(files("../run/libraries/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-1.21.11-R0.1-SNAPSHOT.jar"))
}
