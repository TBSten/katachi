// The build of the katachi IDE plugin (IntelliJ IDEA / Android Studio, build 261 and up).
// Generated from the intellij-plugin-dev skill's scaffold; versions live in gradle/libs.versions.toml.
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    // Only for the standalone Compose Desktop of the headless preview.
    alias(libs.plugins.compose.multiplatform)
    // No version here: it is set in settings.gradle.kts, and setting it twice collides.
    id("org.jetbrains.intellij.platform")
}

group = "me.tbsten.katachi.intellij"
// The plugin's own version, apart from katachi's: `katachiIntellij` in the root
// gradle/libs.versions.toml, read through settings.gradle.kts. The zip attached to each GitHub
// Release (publish.yml) is named after it.
version = katachiLibs.versions.katachiIntellij.get()

// JBR 21, which the 261 platform runs on.
kotlin {
    jvmToolchain(21)
    // Every compiler warning fails the build, in every source set (main, test, preview, uiTest,
    // integrationTest): a warning is fixed, or suppressed at the one place with the reason next to it.
    compilerOptions { allWarningsAsErrors.set(true) }
}

// The UI Composables in src/shared/kotlin are compiled twice: into the plugin against the IDE's
// bundled Jewel, and into `preview` against standalone Jewel, so the headless PNGs show what ships.
sourceSets {
    main { kotlin.srcDir("src/shared/kotlin") }
    create("preview") { kotlin.srcDir("src/shared/kotlin") }
}
val previewImplementation: Configuration = configurations.getByName("previewImplementation")

dependencies {
    intellijPlatform {
        intellijIdea(libs.versions.intellijIdea.get())
        // Brings the Analysis API (K2), so analyze { } works without further dependencies.
        bundledPlugin("org.jetbrains.kotlin")
        // Gradle sync data and ExternalSystem runTask. Pairs with <depends>com.intellij.gradle</depends>.
        bundledPlugin("com.intellij.gradle")
        // Jewel, Compose and Skiko come from the IDE rather than from the plugin. Each line pairs
        // with a <module name="..."/> in plugin.xml.
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.jewel.ideLafBridge")
        bundledModule("intellij.libraries.compose.runtime.desktop")
        // Named explicitly because it does not put runtime on the compile classpath by itself.
        bundledModule("intellij.libraries.compose.foundation.desktop")
        bundledModule("intellij.libraries.skiko")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(libs.junit4)
    // The pure-JVM preview gates (PreviewChecks) are tested from `test`. Standalone Compose is
    // deliberately not put on the test classpath, where it would clash with the bundled one.
    testImplementation(sourceSets["preview"].output)

    // renderComposeScene lives here, Skiko included.
    previewImplementation(compose.desktop.currentOs)
    val jewelForIde = libs.versions.jewelForIde.get()
    previewImplementation("org.jetbrains.jewel:jewel-int-ui-standalone:${libs.versions.jewel.get()}-$jewelForIde")
    // Without it AllIconsKeys render as magenta placeholders in the standalone preview.
    previewImplementation("com.jetbrains.intellij.platform:icons:$jewelForIde")
}

intellijPlatform {
    // A small plugin: skip starting a headless IDE to index settings.
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            // No upper bound: every future build is declared compatible. The zip is installed from
            // disk (no Marketplace), so the IDE never updates it and a later IDE may break it; the
            // nightly workflow runs verifyPlugin against IDEA and Android Studio (below and
            // ide-plugin-nightly.yml) to find that out first. Narrow to "261.*" if that gate is dropped.
            untilBuild = provider { null }
        }
    }
    // `./gradlew verifyPlugin` checks binary compatibility against the IDEs configured here. It
    // downloads those IDEs (recommended() resolves IDEA IU/IC for the since/until range above), so
    // it runs on the nightly workflow rather than on every build (ide-plugin-nightly.yml).
    // Android Studio is added explicitly: recommended() only resolves the base platform's own
    // family (IDEA), and katachi ships to Android Studio users too (research-android-studio.md).
    // The version must be the exact "version" field from jb.gg/android-studio-releases-list.json
    // (four components; the marketing name "2026.1.4" or the platform build "261.26222.65" do not
    // resolve). "2026.1.4.8" is Android Studio Quail 4 Patch 1, whose platform build matches
    // libs.versions.toml's jewelForIde (261.26222.65); bump both together.
    // The plugin ID includes "intellij" (me.tbsten.katachi.intellij), which the Plugin Verifier
    // flags as a Marketplace naming convention warning unrelated to compatibility; muted here
    // rather than renaming the ID, which is unrelated to and out of scope for this task.
    pluginVerification {
        freeArgs = listOf("-mute", "TemplateWordInPluginId")
        // Besides what breaks users (binary incompatibility, misused API), the verifier's warnings
        // fail too, as a compiler warning does: deprecated, removal-scheduled or internal API, and
        // the structure and dependency warnings. Two levels stay allowed. EXPERIMENTAL_API_USAGES:
        // katachi needs the write-intent lock on the EDT, and every way to take it in 261
        // (writeIntentReadAction, WriteIntentReadAction) is experimental. NOT_DYNAMIC: whether the
        // plugin can be loaded without a restart is a property of the plugin, not a warning.
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.COMPATIBILITY_WARNINGS,
            FailureLevel.INVALID_PLUGIN,
            FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
            FailureLevel.MISSING_DEPENDENCIES,
            FailureLevel.DEPRECATED_API_USAGES,
            FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            FailureLevel.INTERNAL_API_USAGES,
            FailureLevel.OVERRIDE_ONLY_API_USAGES,
            FailureLevel.NON_EXTENDABLE_API_USAGES,
        )
        ides {
            recommended()
            create(IntelliJPlatformType.AndroidStudio, "2026.1.4.8")
        }
    }
}

// The Driver smoke (channel D): starts a real IDE with the built plugin through Starter and drives
// it from JUnit 5. A source set of its own, as Starter is JUnit 5 only while `test` is JUnit 4.
// Run on demand with `./gradlew integrationTest` (it opens IDE windows; not part of `check`).
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}
// The configurations are named by string: accessors are generated only for source sets that exist
// when the plugins block is applied, and Gradle 9.6 deprecates the `by getting` delegates.
configurations.getByName("integrationTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
dependencies {
    intellijPlatform { testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation") }
    "integrationTestImplementation"(libs.junit.jupiter)
    "integrationTestRuntimeOnly"(libs.junit.platform.launcher)
    "integrationTestImplementation"(libs.kodein.di)
    "integrationTestImplementation"(libs.kotlinx.coroutines.core)
    // Starter brings kotlin-reflect 2.3.20 while Kodein pulls stdlib 2.1; reflect then fails on a
    // class the older stdlib lacks (KotlinGenericDeclaration). Only this JVM runs it, not the plugin.
    "integrationTestRuntimeOnly"(libs.kotlin.stdlib.starter)
    // The Compose Compiler plugin runs on every compilation and fails without a runtime to see;
    // the smoke has no Composables, so the runtime is there for the compiler only.
    "integrationTestCompileOnly"(compose.runtime)
}
intellijPlatformTesting.testIdeUi.register("integrationTest") {
    task {
        // `registering` alone does not make testIdeUi pick up the source set: connect its classes.
        val integrationTestSourceSet = sourceSets.getByName("integrationTest")
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        useJUnitPlatform()
        // The sample the smoke opens, and the IDE it starts (the build SDK's own distribution).
        systemProperty("katachi.smoke.sampleProject", rootDir.resolve("../sample/jvm").canonicalPath)
        systemProperty("katachi.smoke.ideHome", intellijPlatform.platformPath.toString())
        // Where the smoke copies the repository to open sample/jvm without touching the original.
        systemProperty("katachi.smoke.workDir", layout.buildDirectory.dir("integrationTest").get().asFile.path)
        // Starter reports through Allure, which otherwise writes allure-results/ into this directory.
        systemProperty(
            "allure.results.directory",
            layout.buildDirectory.dir("integrationTest/allure-results").get().asFile.path
        )
    }
}

// uiTest: the property-based tests that drive the ViewModel through sequences of operations and
// render every state they reach, and the tests that operate the real Composables (compose ui-test).
// Both need standalone Compose, which cannot share a classpath with the IDE's bundled one, so the
// platform-free sources they drive are compiled into this source set once more, as `preview` does
// with src/shared/kotlin: the data layer, the ViewModel and the test fakes.
// `test` runs it too, with few iterations; `-Pkatachi.pbt.scale=N` runs N times as many sequences.
sourceSets {
    create("uiTest") {
        kotlin.srcDir("src/uiTest/kotlin")
        kotlin.srcDir("src/shared/kotlin")
        kotlin.srcDir("src/main/kotlin/me/tbsten/katachi/intellij/data")
        kotlin.srcDir("src/main/kotlin/me/tbsten/katachi/intellij/presentation")
        kotlin.srcDir("src/test/kotlin/me/tbsten/katachi/intellij/testing")
        resources.srcDir("src/test/resources")
    }
}
dependencies {
    "uiTestImplementation"(compose.desktop.currentOs)
    "uiTestImplementation"(libs.compose.ui.test.junit4)
    "uiTestImplementation"("org.jetbrains.jewel:jewel-int-ui-standalone:${libs.versions.jewel.get()}-${libs.versions.jewelForIde.get()}")
    "uiTestImplementation"("com.jetbrains.intellij.platform:icons:${libs.versions.jewelForIde.get()}")
    "uiTestImplementation"(libs.kotest.property)
    "uiTestImplementation"(libs.junit4)
    // The ViewModel logs through the platform's Logger; only that jar, not the platform with its Compose.
    "uiTestImplementation"(
        files(
            configurations.named("intellijPlatformClasspath")
                .map { platform -> platform.filter { it.name == "util-8.jar" } })
    )
}
val uiTest = tasks.register<Test>("uiTest") {
    group = "verification"
    description = "Property-based and UI tests over the real Composables on standalone Compose."
    val uiTestSourceSet = sourceSets.getByName("uiTest")
    testClassesDirs = uiTestSourceSet.output.classesDirs
    classpath = uiTestSourceSet.runtimeClasspath
    jvmArgs("-Djava.awt.headless=true", "-Dskiko.renderApi=SOFTWARE")
    providers.gradleProperty("katachi.pbt.scale").orNull?.let { systemProperty("katachi.pbt.scale", it) }
    providers.gradleProperty("katachi.pbt.seed").orNull?.let { systemProperty("katachi.pbt.seed", it) }
    // Where a failing property writes its shrunk operation sequence and the PNG of the state it failed on.
    systemProperty("katachi.pbt.outDir", layout.buildDirectory.dir("uiTest-failures").get().asFile.path)
}
tasks.test { dependsOn(uiTest) }

// K2 for the Analysis API in tests, paired with <supportsKotlinPluginMode supportsK2="true"/>.
// No useJUnitPlatform(): BasePlatformTestCase is JUnit 4 based.
tasks.test { systemProperty("idea.kotlin.plugin.use.k2", "true") }

// updatePreview / verifyPreview: run the preview main() on the standalone classpath.
// The first argument picks the mode; the working directory is this project directory.
fun registerPreviewTask(name: String, mode: String, desc: String) = tasks.register<JavaExec>(name) {
    group = "preview"
    description = desc
    mainClass.set("me.tbsten.katachi.intellij.preview.PreviewMainKt")
    classpath = sourceSets["preview"].runtimeClasspath
    jvmArgs("-Djava.awt.headless=true", "-Dskiko.renderApi=SOFTWARE")
    args(mode)
}
registerPreviewTask(
    "updatePreview",
    "update",
    "Render preview PNGs, write the gallery, and force-refresh the golden snapshots."
)
registerPreviewTask(
    "verifyPreview",
    "verify",
    "Render preview PNGs and fail the build if any differs from the golden snapshots (VRT gate)."
)
