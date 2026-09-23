plugins {
    id("buildsrc.convention.kotlin-jvm")
    // Maven Central へ出すのはこの2モジュールだけ。:architecture-test には付けない。
    id("buildsrc.convention.katachi-publish")
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
    //
    // `@ExperimentalKatachiApi` is opted in for exactly the same reason, even though it says
    // something different ("this will change" rather than "do not touch"). The wall is there so
    // that a *consumer* is aware of depending on a shape that still moves; katachi is the one
    // moving it, so writing `@OptIn` inside `:katachi` would say nothing to anyone. Spelling it
    // out per file would also be worse than pointless here: the compiler reports an opt-in that
    // is already covered module-wide as an unnecessary-opt-in warning.
    compilerOptions {
        optIn.add("me.tbsten.katachi.InternalKatachiApi")
        optIn.add("me.tbsten.katachi.ExperimentalKatachiApi")
    }
}

dependencies {
    // katachi itself has no runtime dependencies; kotest is test-only.
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}
