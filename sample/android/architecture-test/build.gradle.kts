// The recommended way to adopt katachi: one plain JVM module that holds the architecture
// definition and the test that asserts it. It belongs to no layer of the app, and it is a
// plain kotlin("jvm") module even when the project is Android or KMP, because katachi
// itself is a JVM library.
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
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
    // `me.tbsten.katachi:katachi:0.1.0-SNAPSHOT`, which does not exist in any repository.
    // The `includeBuild("../..")` in settings.gradle.kts substitutes it with the local
    // project, so a broken composite build fails loudly instead of silently resolving.
    testImplementation(libs.katachi)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    // `ProjectArchitectureTest` — the one test a user writes — is a plain JUnit 5 test, so
    // the sample shows the adoption step in the shape a project is most likely to already
    // have. kotest-runner-junit5 brings `junit-jupiter-api` along transitively but not the
    // Jupiter *engine*, and without the engine a `@Test` method is silently never run, so
    // the aggregate artifact is declared here on purpose. kotest's own engine keeps
    // running the `*Spec` classes next to it.
    testImplementation(sampleLibs.junitJupiter)
}
