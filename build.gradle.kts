@file:Suppress("AvoidDuplicateDependencies")

plugins {
    alias(libs.plugins.fabric.loom)
}

val minecraftVersion: String = libs.versions.minecraft.get()
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
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    implementation(libs.owo.lib)
    compileOnly(libs.modmenu)

    implementation(libs.commons.compress)
    include(libs.commons.compress)
    runtimeOnly(libs.xz)
    include(libs.xz)
    implementation(libs.juniversalchardet)
    include(libs.juniversalchardet)

    val duckdbNolib = variantOf(libs.duckdb.jdbc) { classifier("nolib") }
    api(duckdbNolib)
    include(duckdbNolib)
    testRuntimeOnly(variantOf(libs.duckdb.jdbc) { classifier(duckdbNativeClassifier()) })

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

tasks.withType<Jar>().configureEach {
    from(rootDir) {
        include("LICENSE")
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

fun duckdbNativeClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val normalizedArch = when (arch) {
        "amd64", "x86_64" -> "amd64"
        "aarch64", "arm64" -> "arm64"
        else -> arch
    }
    return when {
        os.startsWith("windows") -> "windows_$normalizedArch"
        os.startsWith("mac") -> "macos_universal"
        else -> "linux_$normalizedArch"
    }
}
