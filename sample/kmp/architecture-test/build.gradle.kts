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
    // No version is written: resolved from the composite build declared in
    // `pluginManagement` of settings.gradle.kts. A real user writes
    // `id("me.tbsten.katachi") version "<version>"` and resolves it from mavenCentral.
    id("me.tbsten.katachi")
}

kotlin {
    jvmToolchain(17)
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

katachi {
    architecture = "com.example.kmp.projectArchitecture"
    processors {
        // Same as sample/android: no konsist dependency and no serialization compiler
        // plugin in this module, so only an ArchitectureProcessorNoArg processor is
        // registered.
        register("layout", "me.tbsten.katachi.check.LayoutCheck")
        // The typed block, rather than `--arg outputDir=...` on every invocation. `docs`
        // itself needs no `register`: the plugin registers it by default.
        //
        // The pages are written into the repository rather than under `build/`, which means
        // `gitTracked()` offers them to the check like any other file -- the
        // `GeneratedDocumentation` role in the `tool` group is what declares them, and
        // without it the run fails with `[UnexpectedDirectory] docs`. A `Directory` rather
        // than a string, because `outputDir` is resolved against the directory the task runs
        // in and that is `:architecture-test`, one level below the root.
        //
        // CI adds `--arg mode=check` to the same invocation, so pages that are out of date
        // with the definition fail the build instead of being silently stale.
        docs {
            outputDir = rootProject.layout.projectDirectory.dir("docs")
            // Neither `rootTitle` nor `rootDescription` here, on purpose: this is the sample
            // that shows what the defaults look like. `rootTitle` falls back to the
            // convention the plugin sends -- `rootProject.name` -- so the generated root page
            // heads itself `# katachi-sample-kmp ドキュメント` with no paragraph under it.
            // sample/jvm writes both values and sample/android writes only the description.
        }
    }
}
