plugins {
    // Registers the toolchain download repository for the `buildSrc` build, which is a
    // build of its own: the root settings file's resolver does not reach it, so without
    // this the `jvmToolchain(17)` in buildSrc/build.gradle.kts fails with "Toolchain
    // download repositories have not been configured." on a machine with no local JDK 17.
    //
    // No `version` here, on purpose: the root settings file already puts this plugin on
    // the settings classpath, and repeating the version fails with "the plugin is already
    // on the classpath with an unknown version".
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {

    // Use Maven Central and the Gradle Plugin Portal for resolving dependencies in the shared build logic (`buildSrc`) project.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    // Reuse the version catalog from the main build.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "buildSrc"