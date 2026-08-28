rootProject.name = "BetterStresstestbots"

// Default/offline build target: core + 1.21.11 (Paper's Mojang-mapped 1.21.11 jar).
// The other modules are only included when `-Pfull` / `BUILD_FULL=true` is set and the
// required JDK is available (1.21 uses paperweight, 26.x needs JDK 25/26).
val includeFull = System.getProperty("full") == "true" || System.getenv("BUILD_FULL") == "true"

include("core", "v1_21_11")
if (includeFull) {
    include("v1_21")

    val javaSpecification = System.getProperty("java.specification.version") ?: "17"
    val javaMajor = javaSpecification.substringBefore(".").toIntOrNull() ?: 17
    if (javaMajor >= 25) include("v26.1.x")
    if (javaMajor >= 26) include("v26.2")
}
