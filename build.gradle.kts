subprojects {
    apply(plugin = "java")
    configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
    group = "me.micahcode"
    version = "2.0.0"

    // The shared GitHub Actions workflow runs `./gradlew build` on JDK 21.
    // Build the requested/jar-producing 1.21.11 module by default. Other modules
    // are built through `buildAll` or explicit task names (e.g. `:v1_21:assemble`).
    tasks.named("build") {
        if (project.name != "v1_21_11") dependsOn.clear()
    }
}

val includedProjects = gradle.rootProject.subprojects.map { it.name }.toSet()
val hasV26_1 = "v26.1.x" in includedProjects
val hasV26_2 = "v26.2" in includedProjects

tasks.register<Copy>("buildAll") {
    group = "build"
    description = "Build all version jars and collect in build/dist"

    dependsOn(":v1_21:assemble", ":v1_21_11:assemble")
    if (hasV26_1) dependsOn(":v26.1.x:assemble")
    if (hasV26_2) dependsOn(":v26.2:assemble")

    from(project(":v1_21").layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-dev*")
    }
    from(project(":v1_21_11").layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-dev*")
    }
    if (hasV26_1) {
        from(project(":v26.1.x").layout.buildDirectory.dir("libs")) {
            include("*.jar")
            exclude("*-dev*")
        }
    }
    if (hasV26_2) {
        from(project(":v26.2").layout.buildDirectory.dir("libs")) {
            include("*.jar")
            exclude("*-dev*")
        }
    }

    into(layout.buildDirectory.dir("dist"))

    doLast {
        println("Jars collected in build/dist/ yipie (1.21 is for 1.21 to 1.21.4 and rest of 1.21 is for 1.21.11)")
    }
}