// `sample/jvm` is a standalone Gradle build, not a subproject of the katachi build.
// That is what makes it a real integration test: it consumes katachi exactly the way a
// user would, through the published coordinates `me.tbsten.katachi:katachi`.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Resolves the JDK 21 toolchain requested by `jvmToolchain(21)` (D16).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        // A JVM-only sample: no Android artifacts, so `google()` is not needed here.
        mavenCentral()
    }
    versionCatalogs {
        // Two catalogs (D2). The root catalog is the single source of truth for the
        // Kotlin, katachi and kotest versions, so the sample cannot drift from the
        // library it is testing.
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
        // Everything only this sample needs (Ktor, logback) lives next to the sample, to
        // keep the root catalog free of dependencies katachi itself does not have.
        create("sampleLibs") {
            from(files("gradle/sample.versions.toml"))
        }
    }
}

// Must differ from the included build's root project name ("katachi"), otherwise the two
// builds collide.
rootProject.name = "katachi-sample-jvm"

// Pulls katachi in as a composite build. No `dependencySubstitution { }` is needed: the
// substitution is derived from `group` + project name, so `me.tbsten.katachi:katachi`
// resolves to the local `:katachi` project. Verify with
// `./gradlew :architecture-test:dependencyInsight --configuration testRuntimeClasspath
// --dependency katachi` and look for "(by composite build)".
includeBuild("../..")

// The recommended way to adopt katachi: one plain JVM module holding the architecture
// definition and the test that asserts it. Keeping it out of `:` (the application module)
// is what makes the adoption steps identical for a JVM, an Android and a KMP project.
include(":architecture-test")
