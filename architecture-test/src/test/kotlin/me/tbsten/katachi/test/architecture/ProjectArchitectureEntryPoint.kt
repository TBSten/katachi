package me.tbsten.katachi.test.architecture

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.diff.DiffReport
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.processor.internal.KatachiEntryPoint

/**
 * What the Gradle plugin would generate from a `katachi { }` block, written by hand: this module
 * cannot apply the plugin of the build it belongs to, and `katachiDiff` in build.gradle.kts
 * loads this object by name the way the plugin's tasks load the generated one.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
object ProjectArchitectureEntryPoint : KatachiEntryPoint {
    override val architecture: Architecture = projectArchitecture

    override val processors: Map<String, Class<*>> = mapOf(
        "diff" to DiffReport::class.java,
    )
}
