plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.warnings-as-errors")
    alias(libs.plugins.jmh)
}

// Neither `group` nor `version` is set, and no publishing plugin is applied: this module is
// never published. Nothing here reaches the POM or module metadata of `:katachi`,
// `:katachi-konsist` or `:katachi-gradle-plugin`.
//
// The `jmh` task is not wired into `check` (the plugin does not do it, and neither does this
// file), so `./gradlew check` compiles nothing here beyond the empty main / test source sets.
// Run the benchmarks on demand:
//
//   ./gradlew :benchmark:jmh                      # the defaults each benchmark declares
//   ./gradlew :benchmark:jmh -Pjmh.quick=true     # 1 fork, 1 short iteration: a smoke run
//   ./gradlew :benchmark:jmh -Pjmh.includes=Walk  # only the benchmarks matching a regex
//
// The results go to build/results/jmh/results.json, in JMH's JSON format, which
// github-action-benchmark reads as it is.

kotlin {
    compilerOptions {
        optIn.add("me.tbsten.katachi.ExperimentalKatachiApi")
        // `validate(fileSystem)`, `flattenLayout`, `moduleIndex` and `LayoutEntry.glob`. Only
        // @InternalKatachiApi declarations; nothing `internal` to :katachi is reached.
        optIn.add("me.tbsten.katachi.InternalKatachiApi")
    }
}

dependencies {
    jmhImplementation(project(":katachi"))
    jmhImplementation(project(":katachi-konsist"))
    jmhImplementation(testFixtures(project(":katachi")))
}

// The runner that measures katachi on a real project checked out elsewhere (a pinned commit of
// nowinandroid in the nightly workflow). A source set of its own rather than more of `jmh`:
// it is one plain JVM run of `validate()` against a directory on disk, with no JMH harness,
// and it must not end up in the JMH jar. Like `jmh`, nothing in `check` compiles it.
val realProject: SourceSet = sourceSets.create("realProject")

dependencies {
    "realProjectImplementation"(project(":katachi"))
    "realProjectImplementation"(project(":katachi-konsist"))
}

// ./gradlew :benchmark:measureRealProject \
//     -PrealProject.name=nowinandroid -PrealProject.dir=/path/to/nowinandroid
//
// Writes github-action-benchmark's `customSmallerIsBetter` JSON to
// build/results/real-project/<name>.json (or -PrealProject.output=<file>). The measured
// project is only read: katachi's own violations there are expected and do not fail the run.
val measureRealProject = tasks.register<JavaExec>("measureRealProject") {
    group = "benchmark"
    description = "Measures validate() on a real project checked out at -PrealProject.dir."
    classpath = realProject.runtimeClasspath
    mainClass = "me.tbsten.katachi.benchmark.realproject.RealProjectMainKt"
    maxHeapSize = "2g"
    val name = providers.gradleProperty("realProject.name").getOrElse("nowinandroid")
    val output = providers.gradleProperty("realProject.output")
        .getOrElse(layout.buildDirectory.file("results/real-project/$name.json").get().asFile.path)
    args(
        listOfNotNull(
            "--name=$name",
            providers.gradleProperty("realProject.dir").orNull?.let { "--dir=$it" },
            "--output=$output",
            providers.gradleProperty("realProject.warmups").orNull?.let { "--warmups=$it" },
            providers.gradleProperty("realProject.iterations").orNull?.let { "--iterations=$it" },
        ),
    )
}

val quick = providers.gradleProperty("jmh.quick").map { it.toBoolean() }.getOrElse(false)
val includesPattern = providers.gradleProperty("jmh.includes").orNull

jmh {
    jmhVersion = libs.versions.jmh
    // Allocation per operation (gc.alloc.rate.norm) for every benchmark: it moves far less
    // than time between runs, and is what catches a path being rebuilt over and over.
    profilers.add("gc")
    resultFormat = "JSON"
    resultsFile = layout.buildDirectory.file("results/jmh/results.json")
    failOnError = true
    // Fork / warmup / measurement are declared on each benchmark class (@Fork, @Warmup,
    // @Measurement), not here: a value set here overrides the annotations of every class,
    // and AssertColdBench needs its own (SingleShot, fork 5, no warmup).
    if (quick) {
        // Checks that everything runs, not how fast: the numbers of a quick run mean little,
        // and AssertColdBench is no longer cold once a warmup iteration has run before it.
        fork = 1
        warmupIterations = 1
        iterations = 1
        warmup = "1s"
        timeOnIteration = "1s"
    }
    if (includesPattern != null) {
        includes.add(includesPattern)
    }
}
