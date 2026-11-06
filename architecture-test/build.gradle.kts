plugins {
    // buildSrc's convention plugin. `jvmToolchain(17)`, `useJUnitPlatform()` and the test
    // logging all arrive with it, so nothing about the test task is repeated here. The three
    // samples write `alias(libs.plugins.kotlinJvm)` plus those settings by hand instead,
    // because they are standalone builds that cannot see this repository's buildSrc.
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.warnings-as-errors")
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

    // `-Dkatachi.snapshot.update=true` on the command line sets a property of Gradle's own JVM,
    // not of the forked test JVM, so LayerImportSnapshotSpec would never see it without this.
    systemProperty(
        "katachi.snapshot.update",
        providers.systemProperty("katachi.snapshot.update").getOrElse("false"),
    )

    // The violations as SARIF 2.1.0, next to the JUnit report: ci.yml hands this file to
    // GitHub's code scanning (`upload-sarif`), which annotates a pull request with them.
    //
    // This module does not apply the katachi Gradle plugin, so what `katachi { report { sarif
    // = true } }` would wire is written here by hand: the two system properties katachi's
    // `assert()` reads. The second one is where it looks up the files that declared the
    // architecture (the file name is all a declaration site remembers); without it the test
    // JVM looks under src/*/kotlin of its working directory, which here is this module too,
    // so it is given for the same reason the plugin gives it -- the same input either way.
    val sarifFile = layout.buildDirectory.file("reports/katachi/katachi.sarif")
    systemProperty("katachi.report.sarif", sarifFile.get().asFile.absolutePath)
    systemProperty(
        "katachi.report.sourceRoots",
        kotlin.sourceSets["test"].kotlin.srcDirs.joinToString(File.pathSeparator) { it.absolutePath },
    )
    outputs.file(sarifFile)
    // What the plugin's DeleteStaleSarifReports does: the file is written only when assert()
    // runs, so a run that never reaches it (`--tests` on another spec, a failure before it)
    // must not leave the previous run's report for ci.yml's upload-sarif to pick up as this
    // run's. The numbered siblings a second assert() in one JVM writes go with it.
    doFirst {
        val report = sarifFile.get().asFile
        report.delete()
        report.parentFile?.listFiles { _, name -> Regex("katachi-\\d+\\.sarif").matches(name) }?.forEach { it.delete() }
    }
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
    // `architecture`, `assert` and `FileConstraintCheck` directly.
    testImplementation(project(":katachi"))
    testImplementation(project(":katachi-konsist"))
    // No `:katachi-konture` here, deliberately. This module's rules are Konsist's, and Konsist
    // 0.17.3 cannot share a classpath with Konture 1.0.1 (different kotlin-compiler-embeddable);
    // the `KontureBackend` role only describes the files of `:katachi-konture`.
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    // No `testRuntimeOnly(junitJupiterEngine)`. The samples need it because their
    // `ProjectArchitectureTest` is a plain JUnit test; every spec here is a kotest
    // `FreeSpec`, and kotest's JUnit 5 runner brings its own engine.
}

/**
 * `./gradlew :architecture-test:katachiDiff --arg before=origin/main`, the task the katachi Gradle
 * plugin registers in a module that applies it, written here by hand for the same reason the SARIF
 * wiring above is: this module cannot apply the plugin of the build it belongs to. It runs the same
 * `main` on the same test runtime classpath, with `ProjectArchitectureEntryPoint` standing in for the
 * object the plugin would generate.
 */
abstract class KatachiDiffTask : JavaExec() {
    @get:Input
    @get:Option(option = "arg", description = "Argument of DiffReport.Args as key=value, e.g. before=origin/main. Repeatable.")
    abstract val processorArgs: ListProperty<String>
}

tasks.register<KatachiDiffTask>("katachiDiff") {
    group = "katachi"
    description = "Runs katachi's DiffReport on this repository's own definition. Pass arguments with --arg key=value."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "me.tbsten.katachi.processor.internal.MainKt"
    // The processor resolves its output paths and the git range from here, as the plugin's task does.
    workingDir = projectDir
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "--entry-point=me.tbsten.katachi.test.architecture.ProjectArchitectureEntryPoint",
                "--processor=diff",
            ) + processorArgs.get().map { "--arg=$it" }
        },
    )
    // A git range is not an input Gradle can see, so a second run with the same arguments must still run.
    outputs.upToDateWhen { false }
}
