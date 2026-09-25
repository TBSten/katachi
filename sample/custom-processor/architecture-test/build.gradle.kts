// The recommended way to adopt katachi: one plain JVM module that holds the architecture
// definition and the test that asserts it. In this sample it holds three processors as well --
// a processor reads a definition, so it belongs next to the definition and not in a layer of
// the application.
plugins {
    alias(libs.plugins.kotlinJvm)
    // katachi's own Gradle plugin, which registers `runKatachiProcessor`. No version is
    // written: it is resolved from the composite build declared in `pluginManagement` of
    // settings.gradle.kts. A real user writes `id("me.tbsten.katachi") version "<version>"`
    // and resolves it from mavenCentral.
    id("me.tbsten.katachi")
    // Needed because `RoleTable` has a `@Serializable` Args type. A module that registers only
    // ArchitectureProcessorNoArg processors -- sample/android and sample/kmp are exactly that
    // -- needs no serialization compiler plugin at all.
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    // `-Dkatachi.snapshot.update=true` is set on the Gradle invocation and therefore lands in
    // the daemon's JVM; tests run in a forked JVM that inherits nothing, so it has to be handed
    // over explicitly. Read through `providers` so the configuration cache records a dependency
    // on the property instead of baking in whatever it was first set to.
    systemProperty(
        "katachi.snapshot.update",
        providers.systemProperty("katachi.snapshot.update").getOrElse("false"),
    )
}

dependencies {
    // No version is written here: `libs.katachi` points at the version in this repository's
    // catalog, which is not published yet. The `includeBuild("../..")` in settings.gradle.kts
    // substitutes it with the local project, so a broken composite build fails loudly instead
    // of silently resolving.
    testImplementation(libs.katachi)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    // `ProjectArchitectureTest` -- the one test a user writes -- is a plain JUnit 5 test.
    // kotest-runner-junit5 brings `junit-jupiter-api` along transitively but not the Jupiter
    // *engine*, and without the engine a `@Test` method is silently never run. kotest's own
    // engine keeps running the `*Spec` classes next to it.
    testImplementation(sampleLibs.junitJupiter)
}

katachi {
    architecture = "com.example.projectArchitecture"
    processors {
        // `layout` is deliberately not registered. `ProjectArchitectureTest` already runs that
        // check through `assert()`, and `check` runs that test, so registering it here would
        // only give the same answer twice under a second name. The three registrations below
        // are what this sample exists to show.
        //
        // 1. No arguments at all.
        register("roleFileCount", "com.example.processors.RoleFileCount")
        // 2. Typed arguments, with one module-wide default. `arg("sortBy", "Name")` is what
        //    this module always wants; a `--arg sortBy=Declaration` on the command line wins
        //    over it, which is how the root build's `checkSampleCustomProcessor` run prints the
        //    rows in declaration order without this line changing.
        register("roleTable", "com.example.processors.RoleTable") {
            arg("sortBy", "Name")
        }
        // 3. A check: it returns its own result type and overrides `isFailure`, so a problem
        //    makes `runKatachiProcessor` print `[FAILED]` and exit non-zero.
        register("roleDocCoverage", "com.example.processors.RoleDocCoverage")
        // `docs` needs no `register`: the plugin registers it by default. This block only moves
        // its output. `rootProject.layout.projectDirectory.dir("docs")` is an absolute
        // `Directory` rather than a string, so the path does not depend on which module the
        // task happens to run in -- `outputDir` is resolved against the task's own directory,
        // and this one lives in `:architecture-test`.
        //
        // Out of `build/` and into the repository, which means `gitTracked()` now offers the
        // generated pages to the check like any other file: the `GeneratedDocumentation` role
        // is what keeps them from being `[UnexpectedDirectory] docs`. The root build adds
        // `--arg mode=check` on CI, so a definition edited without regenerating fails there.
        docs {
            outputDir = rootProject.layout.projectDirectory.dir("docs")
            rootTitle = "自作プロセッサのサンプル"
            rootDescription = "katachi の定義を読む processor を、利用者が自分で書く方法だけを見せるサンプル。" +
                "このページ以下はすべて `--processor=docs` が生成したもので、手では書かない。"
        }
    }
}
