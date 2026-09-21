plugins {
    id("buildsrc.convention.kotlin-jvm")
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

kotlin {
    // Every declaration that is part of the published surface has to say so.
    explicitApi()

    // `@InternalKatachiApi` exists to stop *consumers* from depending on internals across the
    // published module boundary — it is not meant to stop katachi from depending on itself.
    // Inside `:katachi` (both main and test), writing `@file:OptIn(InternalKatachiApi::class)`
    // on every file that touches its own internals is pure ceremony, so the whole module opts in
    // here instead. The wall itself is still verified: the samples (separate Gradle builds that
    // depend on the published `katachi` artifact) keep writing `@OptIn` themselves, standing in
    // for real consumers.
    compilerOptions {
        optIn.add("me.tbsten.katachi.dsl.InternalKatachiApi")
    }
}

dependencies {
    // katachi itself has no runtime dependencies; kotest is test-only.
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}
