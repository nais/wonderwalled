val konfigVersion = "1.6.10.0"
val ktorVersion = "2.3.11"
val logstashVersion = "7.4"
val logbackVersion = "1.5.6"
val nimbusJoseJwtVersion = "9.40"

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jmailen.kotlinter") version "4.3.0"
    id("com.github.ben-manes.versions") version "0.51.0"
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
        implementation("io.ktor:ktor-server-netty:${ktorVersion}")
        implementation("io.ktor:ktor-server:${ktorVersion}")
        implementation("io.ktor:ktor-server-auth-jwt:${ktorVersion}")
        implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
        implementation("io.ktor:ktor-serialization-jackson:${ktorVersion}")
        implementation("io.ktor:ktor-client-core:${ktorVersion}")
        implementation("io.ktor:ktor-client-apache:${ktorVersion}")
        implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
        implementation("com.natpryce:konfig:${konfigVersion}")
        implementation("com.nimbusds:nimbus-jose-jwt:${nimbusJoseJwtVersion}")
        implementation("net.logstash.logback:logstash-logback-encoder:${logstashVersion}")
        runtimeOnly("ch.qos.logback:logback-classic:${logbackVersion}")
    }
}
