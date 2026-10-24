rootProject.name = "LCSL"
include(
    "LCSL",
    "LCSLCore",
    "LCSLBoot"
)

val minecraftLibraries = listOf("LCSLTransformerDiscoveryService", "LCSLMultiMCBootstrap")
include(minecraftLibraries)

for (library in minecraftLibraries) {
    project(":$library").projectDir = file("minecraft/libraries/$library")
}
