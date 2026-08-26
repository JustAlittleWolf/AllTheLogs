@file:Suppress("AvoidDuplicateDependencies")

plugins {
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion: String = providers.gradleProperty("minecraft_version").get()
val loaderVersion: String = providers.gradleProperty("loader_version").get()
val fabricApiVersion: String = providers.gradleProperty("fabric_api_version").get()
val owoVersion: String = providers.gradleProperty("owo_version").get()
val archivesBaseName: String = providers.gradleProperty("archives_base_name").get()
val modVersion: String = providers.gradleProperty("mod_version").get()
val mavenGroup: String = providers.gradleProperty("maven_group").get()

group = mavenGroup
version = modVersion
base.archivesName.set("$archivesBaseName+$minecraftVersion")

repositories {
    maven("https://maven.wispforest.io") {
        name = "Wisp Forest"
    }
    maven("https://maven.terraformersmc.com/") {
        name = "Terraformers"
    }
    maven("https://jitpack.io") {
        name = "JitPack"
    }
    mavenCentral()
}

loom {
    mods {
        register("allthelogs") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation("io.wispforest:owo-lib:$owoVersion")
    compileOnly("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}")

    api(libs.duckdb.jdbc)
    include(libs.duckdb.jdbc)
    implementation(libs.commons.compress)
    include(libs.commons.compress)
    runtimeOnly(libs.xz)
    include(libs.xz)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    val properties = mapOf("version" to version)
    inputs.properties(properties)
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    providers.gradleProperty("allthelogs.dataset").orNull?.let {
        systemProperty("allthelogs.dataset", it)
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}
