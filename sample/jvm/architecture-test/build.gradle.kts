// The recommended way to adopt katachi: one plain JVM module that holds the architecture
// definition and the test that asserts it. It belongs to no layer of the app, and it is a
// plain kotlin("jvm") module even when the project is Android or KMP, because katachi
// itself is a JVM library.
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    // `-Dkatachi.snapshot.update=true` is set on the Gradle invocation and therefore lands
    // in the daemon's JVM; tests run in a forked JVM that inherits nothing, so it has to be
    // handed over explicitly. Read through `providers` so the configuration cache records a
    // dependency on the property instead of baking in whatever it was first set to.
    systemProperty(
        "katachi.snapshot.update",
        providers.systemProperty("katachi.snapshot.update").getOrElse("false"),
    )
}

dependencies {
    // No version is written here: `libs.katachi` points at
    // the version in this repository's catalog, which is not published yet.
    // The `includeBuild("../..")` in settings.gradle.kts substitutes it with the local
    // project, so a broken composite build fails loudly instead of silently resolving.
    testImplementation(libs.katachi)
    // Only sample/jvm's definition writes `konsist { }` (in `DomainRoles.kt`'s `Service`
    // role). sample/android and sample/kmp deliberately omit this dependency: they exercise
    // `assert()` with zero constraints declared, which is the standing test that the
    // unevaluated-constraint guard does not false-positive on a project that never adopts
    // `konsist { }` at all.
    testImplementation(libs.katachiKonsist)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    // The user-facing test (`ProjectArchitectureTest`) is a plain JUnit test. kotest's
    // runner only registers kotest's engine, so without this one the test compiles, is
    // discovered by no engine, and reports as passing without ever calling `assert()`.
    testRuntimeOnly(sampleLibs.junitJupiterEngine)
}
