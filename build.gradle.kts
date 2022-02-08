object Versions {
    const val konfig = "1.6.10.0"
    const val kotlinlogging = "2.0.10"
    const val ktor = "1.6.7"
    const val logstash = "7.0.1"
    const val logback = "1.2.9"
}

plugins {
    kotlin("jvm") version "1.6.10"
    id("org.jmailen.kotlinter") version "3.7.0"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("com.github.johnrengelman.shadow") version "7.1.1" apply false
}

allprojects {
    group = "io.nais"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "org.jmailen.kotlinter")
    apply(plugin = "com.github.johnrengelman.shadow")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks {
        withType<org.jmailen.gradle.kotlinter.tasks.LintTask> {
            dependsOn("formatKotlin")
        }
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
        withType<Jar> {
            manifest.attributes["Main-Class"] = "io.nais.WonderwalledKt"
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("io.ktor:ktor-server-netty:${Versions.ktor}")
        implementation("io.ktor:ktor-auth:${Versions.ktor}")
        implementation("io.ktor:ktor-auth-jwt:${Versions.ktor}")
        implementation("io.ktor:ktor-client-core:${Versions.ktor}")
        implementation("io.ktor:ktor-client-apache:${Versions.ktor}")
        implementation("io.ktor:ktor-client-json:${Versions.ktor}")
        implementation("io.ktor:ktor-client-jackson:${Versions.ktor}")
        implementation("com.natpryce:konfig:${Versions.konfig}")
        implementation("io.ktor:ktor-jackson:${Versions.ktor}")
        implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstash}")
        runtimeOnly("ch.qos.logback:logback-classic:${Versions.logback}")
    }
}