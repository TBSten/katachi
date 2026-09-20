// sample/android is a standalone Gradle build: it has its own wrapper and its own
// settings file, exactly like a project that consumes katachi from the outside would.

pluginManagement {
    repositories {
        // AGP is published to Google Maven only.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        // Two catalogs on purpose.
        //
        // `libs` is the repository root catalog: the single source of truth for the
        // Kotlin, katachi and kotest versions, shared with katachi itself and with the
        // other samples. Reading it here is what keeps the sample from drifting.
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
        // `sampleLibs` holds what only this sample needs (AGP). It is named
        // `sample.versions.toml` rather than `libs.versions.toml` so that Gradle does
        // not auto-create a second `libs` catalog from it and clash with the one above.
        create("sampleLibs") {
            from(files("gradle/sample.versions.toml"))
        }
    }
}

plugins {
    // Downloads the JDK a toolchain asks for instead of failing when it is missing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "katachi-sample-android"

// Pull katachi in as a composite build, so `testImplementation(libs.katachi)` resolves
// to the sources in this repository instead of to a published artifact. Verify with:
//   ./gradlew :app:dependencyInsight --configuration debugUnitTestRuntimeClasspath --dependency katachi
includeBuild("../..")

include(":app")
include(":feature:home")
include(":feature:settings")
include(":data")
include(":ui:component")
include(":ui:theme")
include(":ui:core")
include(":navigation")
include(":testing")
