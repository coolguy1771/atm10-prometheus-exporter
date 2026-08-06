pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.parchmentmc.org/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.parchmentmc.org/")
        maven("https://cursemaven.com") { content { includeGroup("curse.maven") } }
    }
}

rootProject.name = "atm10-prometheus-exporter"
include("mc-forge-exporter")
