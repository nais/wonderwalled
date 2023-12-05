object Versions {
    const val konfig = "1.6.10.0"
    const val kotlinlogging = "2.0.10"
    const val ktor = "2.3.6"
    const val logstash = "7.4"
    const val logback = "1.4.14"
    const val nimbusJoseJwt = "9.37.2"
}

plugins {
    kotlin("jvm") version "1.9.20"
    id("org.jmailen.kotlinter") version "4.1.0"
    id("com.github.ben-manes.versions") version "0.50.0"
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks {
        withType<org.jmailen.gradle.kotlinter.tasks.LintTask> {
            dependsOn("formatKotlin")
        }
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
        withType<Jar> {
            manifest.attributes["Main-Class"] = "io.nais.WonderwalledKt"
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("io.ktor:ktor-server-netty:${Versions.ktor}")
        implementation("io.ktor:ktor-server:${Versions.ktor}")
        implementation("io.ktor:ktor-server-auth-jwt:${Versions.ktor}")
        implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktor}")
        implementation("io.ktor:ktor-serialization-jackson:${Versions.ktor}")
        implementation("io.ktor:ktor-client-core:${Versions.ktor}")
        implementation("io.ktor:ktor-client-apache:${Versions.ktor}")
        implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktor}")
        implementation("com.natpryce:konfig:${Versions.konfig}")
        implementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusJoseJwt}")
        implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstash}")
        runtimeOnly("ch.qos.logback:logback-classic:${Versions.logback}")
    }
}