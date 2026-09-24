// =============================================================================
// :katachi-gradle-plugin -- the third published artifact, and the one module in
// this repository with no Kotlin source in it. That is a decision, not an
// oversight; see "Why Java" below.
// =============================================================================
//
// ## Why Java
//
// A Gradle plugin's own types are read by the *embedded* Kotlin compiler of
// whatever Gradle the user runs, because that compiler is what compiles their
// `build.gradle.kts`. katachi supports Gradle 8.0 and up (decided 2026-09-24),
// and Gradle 8.0 embeds Kotlin 1.8.10, which cannot read Kotlin 2.x metadata.
//
// The answer the specification assumed -- drop this module's `languageVersion`
// to 1.8 -- is not available: the Kotlin this repository builds with (2.4.10)
// reports language version 1.8 as *unsupported* rather than merely deprecated
// (`LanguageVersion.FIRST_SUPPORTED` is `KOTLIN_2_0`). The lowest value it
// accepts is 2.0, which is Gradle 8.11's embedded Kotlin -- so writing this
// module in Kotlin would raise the supported Gradle floor from 8.0 to 8.11.
//
// Java class files carry no Kotlin metadata at all, so every Kotlin compiler
// from 1.8 to 2.4 reads them alike. Writing this module in Java keeps the
// decided floor, and makes "no kotlin-stdlib in the published dependencies"
// true by construction rather than by a flag that has to keep being true.

plugins {
    // Applies `java-library`, puts `gradleApi()` on `api` (a file-collection
    // dependency, so it never reaches the POM), puts `gradleTestKit()` on
    // `testImplementation`, registers `pluginUnderTestMetadata` -- which is what
    // `GradleRunner.withPluginClasspath()` reads -- and wires `validatePlugins`
    // into `check`.
    //
    // It has to come BEFORE katachi-publish: that convention picks its publishing
    // platform by asking whether this plugin is applied, and a wrong answer
    // publishes the same coordinates twice.
    `java-gradle-plugin`

    // Maven Central wiring, shared with :katachi and :katachi-konsist.
    // Deliberately NOT `buildsrc.convention.kotlin-jvm` or
    // `buildsrc.convention.katachi-kotlin-library`: both apply the Kotlin Gradle
    // plugin, which would put kotlin-stdlib into this module's POM.
    id("buildsrc.convention.katachi-publish")
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

java {
    // What this module is *compiled with*, matching the rest of the repository.
    // It is not what the plugin runs on -- `release` below is that.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.named<JavaCompile>("compileJava") {
    // The bytecode a *user's Gradle daemon* has to load, which is a different JVM from the
    // one :katachi's own tests run on. Gradle 8.0 -- the oldest version katachi supports --
    // starts on Java 8 through 19, so a target above 8 would quietly cut part of the range
    // this plugin declares. Decided 2026-09-24: 8, not 11.
    //
    // `release` rather than sourceCompatibility/targetCompatibility: it also stops the
    // compiler from linking against JDK APIs that do not exist on 8, which is the half that
    // actually catches mistakes. The toolchain above pins javac to 17, so this is a fixed
    // pair rather than one that moves with whatever JDK happens to be installed.
    options.release.set(8)
    // `-options` is excluded only to silence "source value 8 is obsolete", which JDK 17
    // prints on every JavaCompile configured this way.
    options.compilerArgs.add("-Xlint:all,-options")
    // `CompileOptions.encoding` defaults to null, which lets javac fall back to whatever
    // charset the running platform happens to default to. Set explicitly here too (not only
    // on compileTestJava below) so both compile tasks behave the same regardless of the
    // machine's locale -- see compileTestJava for the concrete failure this avoids.
    options.encoding = "UTF-8"
}

tasks.named<JavaCompile>("compileTestJava") {
    // Deliberately no `release` here. The tests are never published, they run on the
    // toolchain above, and they compile against the `gradleTestKit()` of the Gradle that
    // builds this module -- class files a Java 8 target has no business being held to. What
    // has to load on a Java 8 daemon is `compileJava`'s output, and that is where the
    // constraint is stated.
    options.compilerArgs.add("-Xlint:all")
    // `src/test/java/.../KatachiPluginFunctionalTest.java` has Japanese `@DisplayName`
    // strings, and `CompileOptions.encoding` defaults to null -- javac then reads sources in
    // the platform's default charset. The toolchain above is pinned to Java 17, and UTF-8
    // only becomes javac's own default starting with JDK 18, so a non-UTF-8 locale would
    // mojibake the display names or fail to compile without this.
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    // The javadoc jar Maven Central requires is built from this task, so a doclint error
    // fails a release. `-missing` is dropped because `apply(Project)` documents itself with
    // {@inheritDoc} and has no `@param` of its own; every other check stays on.
    val docletOptions = options
    if (docletOptions is StandardJavadocDocletOptions) {
        docletOptions.addStringOption("Xdoclint:all,-missing", "-quiet")
    }
}

gradlePlugin {
    plugins {
        register("katachi") {
            // Decided 2026-09-24. No `.docs` suffix: the plugin owns one general
            // task, not one task per processor.
            id = "me.tbsten.katachi"
            implementationClass = "me.tbsten.katachi.gradle.KatachiPlugin"
            displayName = "katachi"
            description = "Registers runKatachiProcessor, which runs a katachi processor on " +
                "the architecture definition's own test runtime classpath."
            // `tags`, `website` and `vcsUrl` are read only by the Gradle Plugin
            // Portal, which katachi deliberately does not publish to (Maven
            // Central only, decided 2026-09-24). Left out for that reason.
        }
    }
}

/**
 * The upper bound of the functional-test TestKit version matrix, read out of this
 * repository's own `gradle/wrapper/gradle-wrapper.properties` instead of being duplicated
 * as a literal here. The lower bound stays hardcoded at 8.0 -- the oldest Gradle katachi
 * supports (decided 2026-09-24, see the file header) -- only the upper bound tracks the
 * wrapper.
 *
 * Read through `providers.fileContents(...)` over `layout.projectDirectory.file(...)`
 * rather than plain file I/O, so this stays a tracked configuration-cache input instead of
 * an untracked read that would go stale under `--configuration-cache`.
 *
 * Fails the build if `distributionUrl`'s shape ever changes, rather than silently falling
 * back to a stale version.
 */
val testKitGradleVersionCeiling: Provider<String> =
    providers.fileContents(
        rootProject.layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties"),
    )
        .asText
        .map { properties ->
            Regex("""gradle-([\w.-]+)-bin\.zip""").find(properties)
                ?.groupValues
                ?.get(1)
                ?: error(
                    "Could not find `distributionUrl=...gradle-<version>-bin.zip` in " +
                        "gradle/wrapper/gradle-wrapper.properties. " +
                        "katachi.testkit.gradleVersions' upper bound is derived from it and " +
                        "refuses to silently fall back to a stale version.",
                )
        }

tasks.test {
    // `buildsrc.convention.kotlin-jvm` is what normally configures this, and this
    // module deliberately does not apply it.
    useJUnitPlatform()

    testLogging {
        events("failed", "passed", "skipped")
    }

    // The functional tests download the Gradle distributions they are asked for,
    // so they are slow and network bound on a cold cache. They stay inside the
    // ordinary `test` task all the same: a separate `functionalTest` source set
    // would let the version matrix silently stop running in CI, which is exactly
    // the rot the matrix exists to prevent.
    //
    // The floor and the version this repository itself builds with. Override with
    // `-Dkatachi.testkit.gradleVersions=8.0,9.7.1`.
    systemProperty(
        "katachi.testkit.gradleVersions",
        providers.systemProperty("katachi.testkit.gradleVersions")
            .orElse(testKitGradleVersionCeiling.map { ceiling -> "8.0,$ceiling" })
            .get(),
    )
}

dependencies {
    // Nothing for the plugin itself: `java-gradle-plugin` already put `gradleApi()`
    // on `api`. `gradleKotlinDsl()` is deliberately absent -- it exists to hand
    // *Kotlin* sources the Kotlin DSL extension functions, and this module has
    // none; pulling it in would only drag the building Gradle's kotlin-stdlib onto
    // the compile classpath.
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.junitJupiterParams)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
