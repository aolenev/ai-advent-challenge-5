plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization") version "2.0.21"
    alias(libs.plugins.ktor)
    application
    alias(libs.plugins.flyway)
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

flyway {
    configFiles = arrayOf("src/main/resources/flyway.conf")
    dependencies {
        runtimeOnly(libs.flyway.postgres)
        runtimeOnly(libs.postgresql)
    }
}

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.common)
    implementation(libs.bundles.kodein)
    implementation(libs.caffeine.cache)
    implementation(libs.bundles.exposed)
    implementation(libs.typesafe.config)
    implementation(libs.flyway.postgres)
    implementation(libs.postgresql)
    implementation(libs.hikari.cp)
    implementation(libs.pdfbox)

    implementation("com.anthropic:anthropic-java:2.10.0")
    implementation("com.lordcodes.turtle:turtle:0.10.0")

    testImplementation(kotlin("test"))
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
