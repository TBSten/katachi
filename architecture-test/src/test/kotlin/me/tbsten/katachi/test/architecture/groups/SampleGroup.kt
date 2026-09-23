package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.roles.layoutSnapshot
import me.tbsten.katachi.test.architecture.roles.sampleBuild

/**
 * The roles of `sample/`.
 *
 * Two roles for two very different things. The builds are ignored on purpose — each declares
 * itself and asserts it from its own `:architecture-test` — while the snapshots next to them
 * are files this definition owns and checks. See `roles/SampleBuildRole.kt` for why the two
 * cannot be written as one wildcard.
 */
fun DeclarationContainerScope.sampleGroup() = "sample".group {
    title = "サンプル"

    sampleBuild()
    layoutSnapshot()
}
