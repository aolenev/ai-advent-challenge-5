plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization") version "2.0.21"
    alias(libs.plugins.ktor)
    application
}

group = "ru.aolenev"
version = "1.0.0"

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven") }
    maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
}

application {
    mainClass.set("ru.aolenev.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.common)
    implementation(libs.bundles.kodein)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("printProjectVersion") {
    doLast {
        println(version)
    }
}

tasks.distTar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.distZip{
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}