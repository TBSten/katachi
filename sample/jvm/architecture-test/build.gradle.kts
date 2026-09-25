// The recommended way to adopt katachi: one plain JVM module that holds the architecture
// definition and the test that asserts it. It belongs to no layer of the app, and it is a
// plain kotlin("jvm") module even when the project is Android or KMP, because katachi
// itself is a JVM library.
plugins {
    alias(libs.plugins.kotlinJvm)
    // katachi's own Gradle plugin, which registers `runKatachiProcessor`. No version is
    // written: it is resolved from the composite build declared in `pluginManagement` of
    // settings.gradle.kts. A real user writes `id("me.tbsten.katachi") version "<version>"`
    // and resolves it from mavenCentral.
    id("me.tbsten.katachi")
    // Only needed because this sample writes its own processor with a @Serializable Args
    // type (`RoleNames`, registered below). A module that only registers
    // ArchitectureProcessorNoArg processors -- sample/android and sample/kmp are exactly
    // that -- needs no serialization compiler plugin at all.
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
    // `konsist { }` at all. This is also why `konsist` is registered as a katachi { processors
    // { } } key only here, in `katachi { }` below.
    testImplementation(libs.katachiKonsist)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    // The user-facing test (`ProjectArchitectureTest`) is a plain JUnit test. kotest's
    // runner only registers kotest's engine, so without this one the test compiles, is
    // discovered by no engine, and reports as passing without ever calling `assert()`.
    testRuntimeOnly(sampleLibs.junitJupiterEngine)
}

katachi {
    architecture = "com.example.projectArchitecture"
    processors {
        // katachi's own checks, registered as processors: an argument-free class (not an
        // object), which proves `runKatachiProcessor` can instantiate either shape.
        register("layout", "me.tbsten.katachi.check.LayoutCheck")
        register("konsist", "me.tbsten.katachi.check.KonsistCheck")
        // A processor this sample writes itself, as an `object` with a @Serializable Args
        // type -- see `com.example.processors.RoleNames`.
        register("roleNames", "com.example.processors.RoleNames")
        // `docs` needs no `register`: the plugin registers it by default. This block only
        // moves its output. `rootProject.layout.projectDirectory.dir("docs")` is an absolute
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
            // The only sample that sets both. `rootTitle` defaults to `rootProject.name`
            // (the plugin sends it as a convention), so leaving it out would head the page
            // `# katachi-sample-jvm ドキュメント` -- a Gradle coordinate rather than a name a
            // reader recognises. sample/android sets only `rootDescription` and sample/kmp
            // sets neither, so all three shapes exist somewhere in the repository.
            rootTitle = "Ktor サンプルアプリ"
            rootDescription = "Ktor の小さな HTTP サーバを、katachi で形から説明したもの。" +
                "このページ以下はすべて `--processor=docs` が生成したもので、手では書かない。"
        }
    }
}
