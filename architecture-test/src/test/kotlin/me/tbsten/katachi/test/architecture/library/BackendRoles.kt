package me.tbsten.katachi.test.architecture.library

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.test.architecture.mainPackage

/**
 * The roles of the constraint backends — today there is one, `:katachi-konsist`.
 *
 * A sibling module rather than a source set of `:katachi`, because `:katachi` has no runtime
 * dependencies at all and this one has Konsist as an `api` dependency. That is also why it is
 * its own group: the split is the library's most visible promise, and a reader of the
 * generated documentation should meet it as a boundary and not as one more layer.
 *
 * ## Why no `konsist { }` here
 *
 * `konsist` is the last entry of the layer table, so the layers it may not import are none,
 * and a rule with an empty forbidden list can never reject anything. Writing it would add a
 * constraint that is green by construction and says nothing — worse than absent, because it
 * reads as a rule that is being enforced.
 *
 * What would actually be worth stating here — that the backend touches only katachi's
 * *public* surface — is already enforced by the build: `:katachi-konsist` opts into
 * `@ExperimentalKatachiApi` and deliberately not into `@InternalKatachiApi`, so an internal
 * reach fails to compile. See `katachi-konsist/build.gradle.kts`.
 */
fun ArchitectureScope.backendRoles() {
    "backend".group {
        title = "バックエンド"

        "KonsistBackend" {
            title = "Konsist バックエンド"
            summary = "konsist { } を layout { } の中に書けるようにする。katachi の public な面だけに乗る"
            example("Konsist.kt", "\"...\".konsist { } の入口")
            example("KonsistScope.kt", "Konsist の問い合わせ語彙 + must / mustNot / mustBeEmpty")
            layout {
                ":katachi-konsist".module {
                    mainSourceSet / kotlin / mainPackage / "*".ktFile()
                }
            }
        }
    }
}
