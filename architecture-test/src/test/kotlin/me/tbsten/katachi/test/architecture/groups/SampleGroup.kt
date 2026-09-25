package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.roles.sampleBuild
import me.tbsten.katachi.test.architecture.roles.sampleGuide

/**
 * The roles of `sample/`.
 *
 * The samples themselves are ignored, one `ignore()` line each. Each sample is an independent
 * Gradle build that describes itself with katachi and asserts it from its own
 * `:architecture-test` — including the layout snapshot each keeps in its own `snapshots/` —
 * so repeating their contents here would put the same truth in two places.
 *
 * The group exists anyway, because `sample/` is part of this repository's shape and the
 * reason it is not described here has to be written where a reader will find it. See
 * `roles/SampleBuildRole.kt`.
 */
fun DeclarationContainerScope.sampleGroup() = "sample".group {
    title = "サンプル"

    sampleBuild()
    sampleGuide()
}
