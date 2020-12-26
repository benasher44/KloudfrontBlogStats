import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.21"
    kotlin("plugin.serialization") version "1.4.21"
    id("com.squareup.sqldelight") version "1.4.4"
}

group = "com.benasher44"
version = "1.0-SNAPSHOT"

kotlin {
    //TODO: SQLDelight internal hack
    //explicitApi()
    target {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
    }
}

repositories {
    mavenCentral()
}

sqldelight {
    database("KBSDatabase") {
        packageName = "com.benasher44"
        dialect = "postgresql"
        deriveSchemaFromMigrations = true
    }
}

dependencies {
    implementation("com.squareup.sqldelight:jdbc-driver:1.4.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("org.postgresql:postgresql:42.2.18")
    implementation(platform("software.amazon.awssdk:bom:2.15.53"))
    implementation("software.amazon.awssdk:s3")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}