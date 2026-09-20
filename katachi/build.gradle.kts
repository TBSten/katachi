plugins {
    id("buildsrc.convention.kotlin-jvm")
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

kotlin {
    // Every declaration that is part of the published surface has to say so.
    explicitApi()
}

// NOTE: no blanket `optIn` for the test source set on purpose. A test that reaches for an
// @InternalKatachiApi declaration writes `@OptIn(InternalKatachiApi::class)` itself, so that
// the amount of internal API the tests lean on stays visible in the test sources.

dependencies {
    // katachi itself has no runtime dependencies; kotest is test-only.
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}
