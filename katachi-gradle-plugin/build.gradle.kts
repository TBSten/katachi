plugins {
    `java-gradle-plugin`
    id("buildsrc.convention.katachi-publish")
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
    options.compilerArgs.add("-Xlint:all,-options")
    options.encoding = "UTF-8"
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-Xlint:all")
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
            id = "me.tbsten.katachi"
            implementationClass = "me.tbsten.katachi.gradle.KatachiPlugin"
            displayName = "katachi"
            description = "Registers runKatachiProcessor, which runs a katachi processor on " +
                    "the architecture definition's own test runtime classpath."
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
    useJUnitPlatform()

    testLogging {
        events("failed", "passed", "skipped")
    }

    systemProperty(
        "katachi.testkit.gradleVersions",
        providers.systemProperty("katachi.testkit.gradleVersions")
            .orElse(testKitGradleVersionCeiling.map { ceiling -> "8.0,$ceiling" })
            .get(),
    )
}

dependencies {
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.junitJupiterParams)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
