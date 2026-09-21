pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Resolves the JDK 21 toolchain the modules ask for, so the sample builds on a machine
    // that only has some other JDK installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        // google() first: AGP and the Android artifacts only live there.
        google()
        mavenCentral()
    }
    versionCatalogs {
        // Two catalogs on purpose.
        // `libs` is the repository root catalog: the single source of truth for the Kotlin,
        // katachi and kotest versions shared by katachi itself and all three samples. The
        // Compose compiler plugin is versioned with Kotlin, so it is declared in that file
        // too and read here as `libs.plugins.kotlinPluginCompose` — nothing is added on top.
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
        // `sampleLibs` holds what only this sample needs (AGP). It is deliberately NOT
        // named `gradle/libs.versions.toml`, because Gradle would then auto-create a `libs`
        // accessor from it and collide with the root catalog above.
        create("sampleLibs") {
            from(files("gradle/sample.versions.toml"))
        }
    }
}

rootProject.name = "katachi-sample-kmp"

// Build katachi from source instead of resolving it from a repository. Every
// `me.tbsten.katachi:katachi` dependency is substituted with the `:katachi` project of the
// included build, so this sample always tests the working tree.
includeBuild("../..")

include(":app:android")
include(":data")
include(":feature:home")
include(":feature:settings")
include(":ui")
include(":navigation")
include(":testing")

// NOTE: `app/ios` is intentionally not included. It is a plain directory holding the Xcode
// project, and one of the things this sample exists to exercise is a directory that Gradle
// knows nothing about.
