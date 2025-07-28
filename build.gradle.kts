import org.jetbrains.kotlin.daemon.common.trimQuotes

val konfigVersion = "1.6.10.0"
val ktorVersion = "3.2.2"
val logstashVersion = "8.1"
val logbackVersion = "1.5.18"
val opentelemetryVersion = "1.52.0"
val opentelemetryKtorVersion = "2.18.1-alpha"

plugins {
    application
    kotlin("jvm") version "2.2.0"
    id("org.jmailen.kotlinter") version "5.2.0"
    id("com.github.ben-manes.versions") version "0.52.0"
    id("com.gradleup.shadow") version "8.3.8" apply false
}

allprojects {
    group = "io.nais"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "application")
    apply(plugin = "kotlin")
    apply(plugin = "org.jmailen.kotlinter")
    apply(plugin = "com.gradleup.shadow")

    application {
        mainClass.set("io.nais.WonderwalledKt")
    }

    tasks {
        kotlin {
            jvmToolchain(21)
        }
        jar {
            manifest {
                attributes["Main-Class"] = "io.nais.WonderwalledKt"
            }
        }

        lintKotlin {
            dependsOn("formatKotlin")
        }

        withType<JavaExec>().named("run") {
            environment = file("$rootDir/.env")
                .takeIf { it.exists() }
                ?.readLines()
                ?.filterNot { it.isEmpty() || it.startsWith("#") }
                ?.associate {
                    val (key, value) = it.split("=")
                    key to value.trimQuotes()
                } ?: emptyMap()
            environment("OTEL_SERVICE_NAME", project.name)
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("io.ktor:ktor-server:${ktorVersion}")
        implementation("io.ktor:ktor-server-auth:${ktorVersion}")
        implementation("io.ktor:ktor-server-cio:${ktorVersion}")
        implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
        implementation("io.ktor:ktor-serialization-jackson:${ktorVersion}")
        implementation("io.ktor:ktor-client-cio:${ktorVersion}")
        implementation("io.ktor:ktor-client-core:${ktorVersion}")
        implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
        implementation("com.natpryce:konfig:${konfigVersion}")
        implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:${opentelemetryKtorVersion}")
        implementation("io.opentelemetry:opentelemetry-sdk:${opentelemetryVersion}")
        implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:$opentelemetryVersion")
        implementation("io.opentelemetry:opentelemetry-exporter-otlp:${opentelemetryVersion}")
        implementation("net.logstash.logback:logstash-logback-encoder:${logstashVersion}")
        runtimeOnly("ch.qos.logback:logback-classic:${logbackVersion}")
    }
}

tasks {
    named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class).configure {
        val immaturityLevels = listOf("rc", "cr", "m", "beta", "alpha", "preview") // order is important
        val immaturityRegexes = immaturityLevels.map { ".*[.\\-]$it[.\\-\\d]*".toRegex(RegexOption.IGNORE_CASE) }
        fun immaturityLevel(version: String): Int = immaturityRegexes.indexOfLast { version.matches(it) }
        rejectVersionIf { immaturityLevel(candidate.version) > immaturityLevel(currentVersion) }
    }
}
