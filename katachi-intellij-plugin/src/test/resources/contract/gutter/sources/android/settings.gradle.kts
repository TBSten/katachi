// sample/android is a standalone Gradle build: it has its own wrapper and its own
// settings file, exactly like a project that consumes katachi from the outside would.

pluginManagement {
    // `plugins { id("me.tbsten.katachi") }` is resolved here and nowhere else. The
    // `includeBuild("../..")` further down substitutes *library* coordinates only --
    // plugin resolution never looks at it -- so the same build is included a second time,
    // in this block. Gradle treats the two as one included build; its own integration
    // tests cover a build included both as a plugin build and as a regular one.
    //
    // A real user writes neither line. They keep the repositories below and write
    // `id("me.tbsten.katachi") version "<version>"`. The sample stands in for that with a
    // composite build, so that it always exercises the plugin in this working tree rather
    // than the last one published.
    includeBuild("../..")

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
        // Kotlin, katachi, kotest and JUnit versions, shared with katachi itself and with the
        // other samples. Reading it here is what keeps the sample from drifting.
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
        // `sampleLibs` holds what only this sample needs (AGP, AndroidX, Compose BOM and JUnit 4). It is named
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
//   ./gradlew :architecture-test:dependencyInsight --configuration testRuntimeClasspath --dependency katachi
includeBuild("../..")

include(":app")
include(":architecture-test")
include(":feature:home")
include(":feature:settings")
include(":data")
include(":ui")
include(":navigation")
include(":testing")
