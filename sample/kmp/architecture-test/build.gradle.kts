// The recommended way to adopt katachi: one plain JVM module that holds the architecture
// definition and the test that asserts it. It belongs to no layer of the app, and it is a
// plain kotlin("jvm") module even when the project is Android or KMP, because katachi
// itself is a JVM library.
//
// For this sample that is the difference between having a home and not having one: the
// modules here are Kotlin Multiplatform with Android and iOS targets and no JVM target, so
// before this module existed the katachi tests had to squat in `:app:android`'s unit tests.
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
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
    testImplementation(libs.katachi)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}
