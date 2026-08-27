pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    alias(libs.plugins.foojay.resolver)
}

rootProject.name = "AllTheLogs"
