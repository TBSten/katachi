// An independent build, not a subproject of the katachi repository: one Gradle build cannot mix
// two versions of the Kotlin Gradle Plugin, and a plugin must be compiled with a Kotlin no newer
// than the one bundled in its target IDE. Run every task from this directory
// (or `./gradlew -p katachi-intellij-plugin ...` from the repository root).

// Without this import `intellijPlatform { defaultRepositories() }` below does not resolve.
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // The version is written here only; build.gradle.kts applies the plugin without one, as
    // writing it in both places makes the classpaths collide. Keep it equal to
    // `intellijPlatformGradlePlugin` in gradle/libs.versions.toml, which this block cannot read.
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

rootProject.name = "katachi-intellij-plugin"

dependencyResolutionManagement {
    // The root build's catalog, read only for `katachi`: the plugin is released with katachi's
    // version (build.gradle.kts), and gradle/libs.versions.toml at the root is its single source
    // of truth. This build's own catalog stays `libs`.
    versionCatalogs {
        create("katachiLibs") { from(files("../gradle/libs.versions.toml")) }
    }
    repositories {
        mavenCentral()
        google()
        // The IntelliJ Platform SDK, its bundled plugins and intellij-dependencies (icons etc.).
        intellijPlatform {
            defaultRepositories()
        }
    }
}
