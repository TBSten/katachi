plugins {
    // buildSrc's convention plugin. `jvmToolchain(17)`, `useJUnitPlatform()` and the test
    // logging all arrive with it, so nothing about the test task is repeated here. The three
    // samples write `alias(libs.plugins.kotlinJvm)` plus those settings by hand instead,
    // because they are standalone builds that cannot see this repository's buildSrc.
    id("buildsrc.convention.kotlin-jvm")
}

tasks.test {
    // **This task must never be served from the cache, and must never be up to date.**
    //
    // katachi walks the whole repository when the test runs. Gradle cannot see those files:
    // the declared inputs are this module's test sources and its runtime classpath, and
    // nothing else. Add, move or delete a file anywhere else in the repository and the key
    // is unchanged, so Gradle answers UP-TO-DATE or FROM-CACHE and the check never runs —
    // a guard that silently passes, which is worse than no guard.
    //
    // This is not hypothetical. `README.ja.md` went undeclared from 66243e9 until it was
    // found by hand, through several runs that all reported BUILD SUCCESSFUL.
    //
    // Declaring the repository tree as an input instead would keep the caching, but the
    // exclusions would have to mirror katachi's own scanning rules exactly, and getting
    // them wrong puts the silent pass straight back.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

// Neither `group` nor `version` is set: this module is never published.
// `explicitApi()` is left out too - it has no public surface, only test sources.

dependencies {
    // The one difference from a sample's `architecture-test`. A sample resolves
    // `me.tbsten.katachi:katachi` as an external module and lets `includeBuild("../..")`
    // substitute it, which is the path a real user takes. Here katachi is the build, so the
    // dependency is an ordinary project reference. Writing `libs.katachi` instead would ask
    // for the version in this repository's catalog, which is not published yet and - with no composite build to
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
