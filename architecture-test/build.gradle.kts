plugins {
    // buildSrc's convention plugin. `jvmToolchain(21)`, `useJUnitPlatform()` and the test
    // logging all arrive with it, so nothing about the test task is repeated here. The three
    // samples write `alias(libs.plugins.kotlinJvm)` plus those settings by hand instead,
    // because they are standalone builds that cannot see this repository's buildSrc.
    id("buildsrc.convention.kotlin-jvm")
}

// Neither `group` nor `version` is set: this module is never published.
// `explicitApi()` is left out too - it has no public surface, only test sources.

dependencies {
    // The one difference from a sample's `architecture-test`. A sample resolves
    // `me.tbsten.katachi:katachi` as an external module and lets `includeBuild("../..")`
    // substitute it, which is the path a real user takes. Here katachi is the build, so the
    // dependency is an ordinary project reference. Writing `libs.katachi` instead would ask
    // for 0.1.0-SNAPSHOT, which exists in no repository and - with no composite build to
    // substitute it - would simply fail to resolve.
    //
    // `:katachi-konsist` already exposes `:katachi` and Konsist as `api` dependencies, so the
    // first line is transitively redundant. It is written out because this module names
    // `architecture`, `assert` and `ConstraintCheck` directly.
    testImplementation(project(":katachi"))
    testImplementation(project(":katachi-konsist"))
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    // No `testRuntimeOnly(junitJupiterEngine)`. The samples need it because their
    // `ProjectArchitectureTest` is a plain JUnit test; every spec here is a kotest
    // `FreeSpec`, and kotest's JUnit 5 runner brings its own engine.
}
