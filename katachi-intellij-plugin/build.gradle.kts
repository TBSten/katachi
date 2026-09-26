// The build of the katachi IDE plugin (IntelliJ IDEA / Android Studio, build 261 and up).
// Generated from the intellij-plugin-dev skill's scaffold; versions live in gradle/libs.versions.toml.
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    // Only for the standalone Compose Desktop of the headless preview.
    alias(libs.plugins.compose.multiplatform)
    // No version here: it is set in settings.gradle.kts, and setting it twice collides.
    id("org.jetbrains.intellij.platform")
}

group = "me.tbsten.katachi.intellij"
// TODO: follow katachi's own version once the plugin is published.
version = "0.1.0"

// JBR 21, which the 261 platform runs on.
kotlin { jvmToolchain(21) }

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
            // No upper bound: every future build is declared compatible. TODO: gate it with
            // verifyPlugin on CI, or narrow it to "261.*".
            untilBuild = provider { null }
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
        systemProperty("allure.results.directory", layout.buildDirectory.dir("integrationTest/allure-results").get().asFile.path)
    }
}

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
registerPreviewTask("updatePreview", "update", "Render preview PNGs, write the gallery, and force-refresh the golden snapshots.")
registerPreviewTask("verifyPreview", "verify", "Render preview PNGs and fail the build if any differs from the golden snapshots (VRT gate).")
