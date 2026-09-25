import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    // Only the plain JVM convention, like `:architecture-test`. `katachi-kotlin-library` and
    // `katachi-publish` are deliberately not applied: this module is never published, and
    // applying `org.jetbrains.dokka` here would give it Dokka's own consumable configurations,
    // which the root build's `dokkaHtmlPlugin(project(":tool:dokka"))` would then have to
    // choose between.
    id("buildsrc.convention.kotlin-jvm")
}

kotlin {
    explicitApi()

    // The jar runs inside Dokka's worker, next to the kotlin-stdlib Dokka brings: dokka-core
    // 2.2.0 depends on kotlin-stdlib 2.0.21 (its POM). Keeping the API and the stdlib dependency
    // at 2.0 means this plugin never asks for anything newer than that worker is guaranteed to
    // have. The compiler warns that API version 2.0 is deprecated; that warning is silenced
    // because following it would break the very guarantee above.
    coreLibrariesVersion = "2.0.21"
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

/**
 * The all-modules-page plugin, resolved on its own and handed to the aggregation tests as a
 * `pluginsClasspath` entry instead of being put on the test classpath.
 *
 * On the test classpath, `ServiceLoader` would install it into every Dokka run, where it
 * replaces the single-module generation and the location provider. Passed as
 * `pluginsClasspath`, it is loaded only by the run that asks for it — which is also how the
 * Dokka Gradle plugin keeps it out of the per-module runs.
 */
val allModulesPagePlugin: Configuration = configurations.create("allModulesPagePlugin") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    // Present at run time inside Dokka, so none of them may be packaged or leak into the
    // plugin classpath DGP builds from this project.
    compileOnly(libs.dokkaCore)
    compileOnly(libs.dokkaBase)
    compileOnly(libs.dokkaTemplating)

    testImplementation(libs.dokkaCore)
    testImplementation(libs.dokkaBase)
    testImplementation(libs.dokkaTemplating)
    testImplementation(libs.dokkaTestApi)
    testImplementation(libs.dokkaBaseTestUtils)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    // The Kotlin analysis Dokka needs to read the inline test sources. Found by ServiceLoader.
    testRuntimeOnly(libs.dokkaAnalysisKotlinSymbols)

    add(allModulesPagePlugin.name, libs.dokkaAllModulesPage)
}

/** Passes [classpath] to the test JVM as a system property, tracked as a task input. */
abstract class PluginsClasspathArgument : CommandLineArgumentProvider {
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> =
        listOf("-Dkatachi.dokka.allModulesPagePlugin=${classpath.files.joinToString(File.pathSeparator)}")
}

tasks.test {
    jvmArgumentProviders += objects.newInstance<PluginsClasspathArgument>().apply {
        classpath.from(allModulesPagePlugin)
    }
    // Dokka's analysis is heavy; the default 512m heap is not enough for several runs in one JVM.
    maxHeapSize = "2g"
}
