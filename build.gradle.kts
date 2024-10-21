val konfigVersion = "1.6.10.0"
val ktorVersion = "3.0.0"
val logstashVersion = "8.0"
val logbackVersion = "1.5.11"
val nimbusJoseJwtVersion = "9.41.2"

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jmailen.kotlinter") version "4.4.1"
    id("com.github.ben-manes.versions") version "0.51.0"
    id("com.gradleup.shadow") version "8.3.3" apply false
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
    apply(plugin = "com.gradleup.shadow")

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
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("io.ktor:ktor-server:${ktorVersion}")
        implementation("io.ktor:ktor-server-cio:${ktorVersion}")
        implementation("io.ktor:ktor-server-auth-jwt:${ktorVersion}")
        implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
        implementation("io.ktor:ktor-serialization-jackson:${ktorVersion}")
        implementation("io.ktor:ktor-client-cio:${ktorVersion}")
        implementation("io.ktor:ktor-client-core:${ktorVersion}")
        implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
        implementation("com.natpryce:konfig:${konfigVersion}")
        implementation("com.nimbusds:nimbus-jose-jwt:${nimbusJoseJwtVersion}")
        implementation("net.logstash.logback:logstash-logback-encoder:${logstashVersion}")
        runtimeOnly("ch.qos.logback:logback-classic:${logbackVersion}")
    }
}
