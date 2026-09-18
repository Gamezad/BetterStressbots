plugins {
    java
}

// core is compiled against the committed Paper 1.21.11 API jar plus the
// committed runtime library jars (adventure, bungee-chat, ...) in run/.
// The API jar alone is not a complete classpath: javac must also see the
// transitive supertypes the Bukkit API references (net.kyori.adventure.*,
// net.md_5.bungee.*). Staying on the committed jars keeps the build offline.
dependencies {
    compileOnly(files("../run/libraries/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-1.21.11-R0.1-SNAPSHOT.jar"))
    compileOnly(fileTree("../run/libraries") {
        include("**/*.jar")
        exclude("**/io/papermc/paper/paper-api/**")
    })
}
