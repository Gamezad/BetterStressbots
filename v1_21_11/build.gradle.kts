plugins {
    java
}

// Offline 1.21.11 build: compile against the Paper 1.21.11 server jar and its bundled
// libraries already present in run/. No paperweight/reobf is needed because Paper
// 1.21.11 is Mojang-mapped at runtime.
dependencies {
    implementation(project(":core"))
    compileOnly(files("../run/versions/1.21.11/paper-1.21.11.jar"))
    compileOnly(fileTree("../run/libraries") {
        include("*.jar")
    })
}

tasks.jar {
    archiveBaseName.set("BetterStresstestbots")
    archiveVersion.set("2.0.0")
    archiveClassifier.set("1.21.11")
    from(project(":core").sourceSets.main.output)
}
