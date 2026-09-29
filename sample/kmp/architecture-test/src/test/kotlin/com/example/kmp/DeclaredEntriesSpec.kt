package com.example.kmp

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.process

/**
 * `ArchitectureProcessContext.declaredEntries`, read from outside `:katachi`.
 *
 * Two things are being stood on here, and both of them need to be stood on from *this* side of
 * the wall. `:katachi` opts its own module in to every marker it publishes, so a spec written
 * there cannot tell a reachable API from an unreachable one -- this file can, because the only
 * opt-in it writes is the one a real consumer would write.
 *
 * - `LayoutEntry` and `LayoutEntryKind` have to be reachable with `@ExperimentalKatachiApi`
 *   alone. They used to be `@InternalKatachiApi`, which made `context.declaredEntries` an API
 *   member whose elements nobody outside katachi was allowed to touch.
 * - `declaredEntries` and `filesOf` answer different questions, and a wildcard module key is
 *   where that shows. This sample writes `":feature:*".module(...) { }` in five places, so it is the
 *   one that can say so against a real checkout instead of against an invented tree.
 */
@OptIn(ExperimentalKatachiApi::class)
class DeclaredEntriesSpec : FreeSpec({
    "a processor that only looks at declarations can read paths from declaredEntries" {
        val paths = projectArchitecture.process { context -> context.declaredEntries.map { it.path } }

        // Declared directly under `layout { }` by Gradle/SettingsScript, so no module key is
        // involved and the declared view and the walked view agree about it.
        paths shouldContain "settings.gradle.kts"
    }

    "a wildcard module key appears as the pattern in declaredEntries and expanded in filesOf" {
        // Read both from a single Context. Processing separately could be read as "the answers differ
        // because there are two models", which blurs what we want to pin down (two members of the
        // same model returning different answers.)
        val (declaredFeaturePaths, walkedFeatureFiles) = projectArchitecture.process { context ->
            val declared = context.declaredEntries.map { it.path }.filter { it.startsWith("feature/") }
            val walked = context.filesOf(context.roles.single { it.qualifiedName == "feature.Screen" })
            declared to walked
        }

        // `:feature:*` stands for the feature modules that exist, and which modules exist is a
        // question only the file system can answer -- so the declared view, which reads nothing,
        // keeps the key as the pattern it was written as instead of naming any module.
        declaredFeaturePaths.shouldNotBeEmpty()
        declaredFeaturePaths.all { it.startsWith("feature/*") } shouldBe true
        // The walk does answer it, so the same model hands back the files under those modules --
        // real directories, with no wildcard left in them.
        walkedFeatureFiles.shouldNotBeEmpty()
        walkedFeatureFiles.all { it.startsWith("feature/") } shouldBe true
        walkedFeatureFiles.none { '*' in it } shouldBe true
    }
})
