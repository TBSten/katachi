// `sample/custom-processor` is a standalone Gradle build, not a subproject of the katachi
// build. That is what makes it a real integration test: it consumes katachi exactly the way a
// user would, through the published coordinates `me.tbsten.katachi:katachi`.
//
// What this sample is for, and what it is not: the three other samples show how a *project* is
// described with katachi. This one shows how a *processor* is written -- three of them, each a
// different shape -- so the application below them is deliberately the smallest thing that
// still gives the roles something to cover.

pluginManagement {
    // `plugins { id("me.tbsten.katachi") }` is resolved here and nowhere else. The
    // `includeBuild("../..")` further down substitutes *library* coordinates only — plugin
    // resolution never looks at it — so the same build is included a second time, in this
    // block. Gradle treats the two as one included build; its own integration tests cover a
    // build included both as a plugin build and as a regular one.
    //
    // A real user writes neither line. They keep `mavenCentral()` below and write
    // `id("me.tbsten.katachi") version "<version>"`. The sample stands in for that with a
    // composite build, so that it always exercises the plugin in this working tree rather
    // than the last one published.
    includeBuild("../..")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Resolves the JDK 17 toolchain requested by `jvmToolchain(17)`.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        // A JVM-only sample: no Android artifacts, so `google()` is not needed here.
        mavenCentral()
    }
    versionCatalogs {
        // Two catalogs. The root catalog is the single source of truth for the Kotlin,
        // katachi and kotest versions, so the sample cannot drift from the library it is
        // testing.
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
        // Everything only this sample needs lives next to the sample. Here that is one
        // entry: the JUnit engine that `ProjectArchitectureTest` needs to be discovered.
        create("sampleLibs") {
            from(files("gradle/sample.versions.toml"))
        }
    }
}

// Must differ from the included build's root project name ("katachi"), otherwise the two
// builds collide.
rootProject.name = "katachi-sample-custom-processor"

// Pulls katachi in as a composite build. No `dependencySubstitution { }` is needed: the
// substitution is derived from `group` + project name, so `me.tbsten.katachi:katachi`
// resolves to the local `:katachi` project.
includeBuild("../..")

// The recommended way to adopt katachi: one plain JVM module holding the architecture
// definition and the test that asserts it. In this sample it holds the three processors as
// well -- a processor reads a definition, so it belongs next to the definition rather than in
// a layer of the application.
include(":architecture-test")
