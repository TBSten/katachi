package com.example.kmp

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.process

@OptIn(ExperimentalKatachiApi::class)
class DeclaredEntriesSpec : FreeSpec({
    "a processor that only looks at declarations can read paths from declaredEntries" {
        val paths = projectArchitecture.process { context -> context.declaredEntries.map { it.path } }

        // Declared directly under `layout { }` by Gradle/SettingsScript, so no module key is
        // involved and the declared view and the walked view agree about it.
        paths shouldContain "settings.gradle.kts"
    }

    "a wildcard directory appears as the pattern in declaredEntries and expanded in filesOf" {
        // Read both from a single Context. Processing separately could be read as "the answers differ
        // because there are two models", which blurs what we want to pin down (two members of the
        // same model returning different answers.)
        val (declaredFeaturePaths, walkedFeatureFiles) = projectArchitecture.process { context ->
            val declared = context.declaredEntries.map { it.path }.filter { it.startsWith("feature/") }
            val walked = context.filesOf(context.roles.single { it.qualifiedName == "feature.Screen" })
            declared to walked
        }

        // `feature/*` stands for the feature directories that exist, and which ones exist is a
        // question only the file system can answer -- so the declared view, which reads nothing,
        // keeps the key as the pattern it was written as instead of naming any directory.
        declaredFeaturePaths.shouldNotBeEmpty()
        declaredFeaturePaths.all { it.startsWith("feature/*") } shouldBe true
        // The walk does answer it, so the same model hands back the files under those modules --
        // real directories, with no wildcard left in them.
        walkedFeatureFiles.shouldNotBeEmpty()
        walkedFeatureFiles.all { it.startsWith("feature/") } shouldBe true
        walkedFeatureFiles.none { '*' in it } shouldBe true
    }
})
