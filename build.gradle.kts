
import org.jetbrains.kotlin.daemon.common.trimQuotes
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    application
    alias(libs.plugins.dependency.updates)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinter)
}

allprojects {
    group = "io.nais"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    pluginManager.apply("org.jetbrains.kotlin.jvm")
    pluginManager.apply("org.jmailen.kotlinter")

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }

    tasks.withType<LintTask>().configureEach {
        dependsOn("formatKotlin")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    dependencies {
        implementation(platform(rootProject.libs.jacksonBom))
        implementation(platform(rootProject.libs.jackson2Bom))
        implementation(rootProject.libs.httpclient5)
        implementation(rootProject.libs.konfig)
        implementation(rootProject.libs.ktorClientCio)
        implementation(rootProject.libs.ktorClientContentNegotiation)
        implementation(rootProject.libs.ktorClientCore)
        implementation(rootProject.libs.ktorClientMock)
        implementation(rootProject.libs.ktorSerializationJackson)
        implementation(rootProject.libs.ktorServer)
        implementation(rootProject.libs.ktorServerAuth)
        implementation(rootProject.libs.ktorServerCio)
        implementation(rootProject.libs.ktorServerContentNegotiation)
        implementation(rootProject.libs.ktorServerTestHost)
        implementation(rootProject.libs.logstashEncoder)
        implementation(rootProject.libs.opentelemetryExporterOtlp)
        implementation(rootProject.libs.opentelemetryKtor)
        implementation(rootProject.libs.opentelemetrySdk)
        implementation(rootProject.libs.opentelemetrySdkExtensionAutoconfigure)
        runtimeOnly(rootProject.libs.logbackClassic)
        testImplementation(rootProject.libs.kotlinTest)
    }
}

configure(subprojects.filter { it.name != "wonderwalled-common" }) {
    pluginManager.apply("application")

    extensions.configure<JavaApplication> {
        mainClass.set("io.nais.WonderwalledKt")
    }

    tasks.withType<Jar>().configureEach {
        manifest {
            attributes["Main-Class"] = "io.nais.WonderwalledKt"
        }
    }

    tasks.named<JavaExec>("run") {
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
}

tasks {
    named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class).configure {
        val immaturityLevels = listOf("rc", "cr", "m", "beta", "alpha", "preview") // order is important
        val immaturityRegexes = immaturityLevels.map { ".*[.\\-]$it[.\\-\\d]*".toRegex(RegexOption.IGNORE_CASE) }

        fun immaturityLevel(version: String): Int = immaturityRegexes.indexOfLast { version.matches(it) }
        rejectVersionIf { immaturityLevel(candidate.version) > immaturityLevel(currentVersion) }
    }
}
