package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.roles.gradlePlugin

/**
 * The roles of `:katachi-gradle-plugin`, the third published artifact.
 *
 * A group of its own rather than one more role of `library`, for the same reason `backend` is
 * one: `library` describes the package layers of `:katachi`, and this module is not a layer of
 * anything. It is loaded by the user's Gradle daemon instead of by their tests, it is written
 * in Java instead of Kotlin, and it carries a compatibility range — Gradle 8.0 and up — that
 * nothing else in this repository has. A reader of the generated documentation should meet
 * that as a boundary rather than as a seventh layer.
 */
fun DeclarationContainerScope.pluginGroup() = "plugin".group {
    title = "Gradle プラグイン"

    gradlePlugin()
}
