import org.jetbrains.kotlin.daemon.common.trimQuotes

val konfigVersion = "1.6.10.0"
val ktorVersion = "3.0.1"
val logstashVersion = "8.0"
val logbackVersion = "1.5.12"
val nimbusJoseJwtVersion = "9.46"

plugins {
    application
    kotlin("jvm") version "2.0.21"
    id("org.jmailen.kotlinter") version "4.4.1"
    id("com.github.ben-manes.versions") version "0.51.0"
    id("com.gradleup.shadow") version "8.3.5" apply false
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
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("io.ktor:ktor-server:${ktorVersion}")
        implementation("io.ktor:ktor-server-cio:${ktorVersion}")
        implementation("io.ktor:ktor-server-auth-jwt:${ktorVersion}")
        implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
        implementation("io.ktor:ktor-serialization-jackson:${ktorVersion}")
        constraints {
            // require a newer patch version to fix JsonAnySetter:
            // https://github.com/FasterXML/jackson-databind/issues/4508
            add("implementation", "com.fasterxml.jackson.core:jackson-annotations") {
                version {
                    require("2.18.1")
                }
            }
            add("implementation", "com.fasterxml.jackson.core:jackson-core") {
                version {
                    require("2.18.1")
                }
            }
            add("implementation", "com.fasterxml.jackson.core:jackson-databind") {
                version {
                    require("2.18.1")
                }
            }
            add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin") {
                version {
                    require("2.18.1")
                }
            }
        }
        implementation("io.ktor:ktor-client-cio:${ktorVersion}")
        implementation("io.ktor:ktor-client-core:${ktorVersion}")
        implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
        implementation("com.natpryce:konfig:${konfigVersion}")
        implementation("com.nimbusds:nimbus-jose-jwt:${nimbusJoseJwtVersion}")
        implementation("net.logstash.logback:logstash-logback-encoder:${logstashVersion}")
        runtimeOnly("ch.qos.logback:logback-classic:${logbackVersion}")
    }
}
