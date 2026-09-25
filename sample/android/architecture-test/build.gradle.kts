// The recommended way to adopt katachi: one plain JVM module that holds the architecture
// definition and the test that asserts it. It belongs to no layer of the app, and it is a
// plain kotlin("jvm") module even when the project is Android or KMP, because katachi
// itself is a JVM library.
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

katachi {
    architecture = "com.example.sample.projectArchitecture"
    processors {
        // `konsist` is deliberately not registered here: this module has no
        // `testImplementation(libs.katachiKonsist)` dependency at all -- it is the standing
        // test that `assert()` runs cleanly with zero constraints declared. Registering it
        // as a processor would need a class it cannot see. Only sample/jvm registers it.
        // No serialization compiler plugin is applied to this module either: `layout` is
        // the only processor registered, and it is ArchitectureProcessorNoArg.
        register("layout", "me.tbsten.katachi.check.LayoutCheck")
        // `docs` is not registered here either -- the plugin registers it by default, and
        // this block only says where it writes. Given as a `Directory` rather than a string
        // because `outputDir` is resolved against the directory the task runs in, which is
        // `:architecture-test`, not the root.
        //
        // The pages land in the repository instead of `build/`, so `gitTracked()` hands them
        // to the check: the `GeneratedDocumentation` role in the `tool` group is what declares
        // them. CI runs the same processor with `--arg mode=check`, which turns a definition
        // changed without regenerating into a failure.
        docs {
            outputDir = rootProject.layout.projectDirectory.dir("docs")
            // `rootTitle` is deliberately left out: the plugin sends `rootProject.name` as a
            // convention, so the page heads itself `# katachi-sample-android ドキュメント`.
            // Only `rootDescription` is written, which is the middle of the three shapes --
            // sample/jvm writes both, sample/kmp writes neither.
            rootDescription = "Compose で書かれた Android アプリを、katachi で形から説明したもの。" +
                "このページ以下はすべて `--processor=docs` が生成したもので、手では書かない。"
        }
    }
}
