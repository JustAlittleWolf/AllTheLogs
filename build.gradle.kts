plugins {
    `java-library`
}

group = "me.wolfii"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(libs.duckdb.jdbc)
    implementation(libs.commons.compress)
    runtimeOnly(libs.xz)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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
