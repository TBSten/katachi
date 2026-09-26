// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "katachi"

include(":katachi")

// The Konsist backend. A sibling subproject rather than a source set of `:katachi`, because
// `:katachi` has no runtime dependencies and this one has Konsist as an `api` dependency.
include(":katachi-konsist")

// The Gradle plugin, the third published artifact. A module of its own rather than a source
// set of `:katachi`, and written in Java rather than Kotlin, because it is loaded by the
// user's Gradle daemon and therefore has an entirely different compatibility target
// (Gradle 8.0 / Java 8) from the library (Kotlin 2.2 / JVM 17). See its build.gradle.kts.
include(":katachi-gradle-plugin")

// katachi's own architecture, declared with katachi and asserted like any other test. The
// same recommended shape the three samples use: one independent JVM module that belongs to
// no layer. It is not a source set of `:katachi` because the check walks from the repository
// root (it looks for a `gradlew` above its working directory), so the module holding it has
// to sit at the top level next to the modules it describes.
include(":architecture-test")

// A Dokka plugin used only by this repository's own API reference (`generateApiDocs`): it lists
// the declarations tagged `@featured` in the sidebar, and adds llms.txt files for AI agents.
// Under `tool/` rather than at the top level because it is build tooling, not a published
// artifact; `:tool` is only the implicit parent and has no build file of its own.
include(":tool:dokka")

// Performance benchmarks (JMH), never published and not part of `check`. They run on demand
// (`./gradlew :benchmark:jmh`) and use the synthetic projects from `:katachi`'s test fixtures.
include(":benchmark")
