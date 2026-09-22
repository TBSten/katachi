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

// katachi's own architecture, declared with katachi and asserted like any other test. The
// same recommended shape the three samples use: one independent JVM module that belongs to
// no layer. It is not a source set of `:katachi` because the check walks from the repository
// root (it looks for a `gradlew` above its working directory), so the module holding it has
// to sit at the top level next to the modules it describes.
include(":architecture-test")