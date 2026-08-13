subprojects {
    apply(plugin = "java")
    configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
    group = "me.micahcode"
    version = "2.0.0"
}

tasks.register<Copy>("buildAll") {
    group = "build"
    description = "Build all version jars and collect in build/dist"

    dependsOn(":v1_21:assemble", ":v1_21_11:assemble", ":v26.1.x:assemble", ":v26.2:assemble")

    from(project(":v1_21").layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-dev*")
    }
    from(project(":v1_21_11").layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-dev*")
    }
    from(project(":v26.1.x").layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-dev*")
    }
    from(project(":v26.2").layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("*-dev*")
    }

    into(layout.buildDirectory.dir("dist"))

    doLast {
        println("Jars collected in build/dist/ yipie (1.21 is for 1.21 to 1.21.4 and rest of 1.21 is for 1.21.11)")
    }
}