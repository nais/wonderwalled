import org.jetbrains.kotlin.daemon.common.trimQuotes
import org.jmailen.gradle.kotlinter.tasks.LintTask

val konfigVersion = "1.6.10.0"
val ktorVersion = "3.3.2"
val logstashVersion = "9.0"
val logbackVersion = "1.5.21"
val opentelemetryVersion = "1.56.0"
val opentelemetryKtorVersion = "2.22.0-alpha"

plugins {
    application
    kotlin("jvm") version "2.2.21"
    id("org.jmailen.kotlinter") version "5.3.0"
    id("com.github.ben-manes.versions") version "0.53.0"
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

        withType<LintTask> {
            dependsOn("formatKotlin")
        }

        withType<JavaExec>().named("run") {
            environment = file("local.env")
                .takeIf { it.exists() }
                ?.readLines()
                ?.filterNot { it.isEmpty() || it.startsWith("#") }
                ?.associate {
                    val (key, value) = it.split("=")
                    key to value.trimQuotes()
                } ?: emptyMap()
            environment("OTEL_SERVICE_NAME", project.name)
        }
        test {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("io.ktor:ktor-server:$ktorVersion")
        implementation("io.ktor:ktor-server-auth:$ktorVersion")
        implementation("io.ktor:ktor-server-cio:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
        implementation("io.ktor:ktor-client-cio:$ktorVersion")
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
        implementation("com.natpryce:konfig:$konfigVersion")
        implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:$opentelemetryKtorVersion")
        implementation("io.opentelemetry:opentelemetry-sdk:$opentelemetryVersion")
        implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:$opentelemetryVersion")
        implementation("io.opentelemetry:opentelemetry-exporter-otlp:$opentelemetryVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")
        runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

        // Common test dependencies
        implementation("io.ktor:ktor-client-mock:$ktorVersion")
        implementation("io.ktor:ktor-server-test-host:$ktorVersion")
        testImplementation(kotlin("test"))
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
