package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext

// Named, not anonymous: projectWalk and withArgs must see through it to base.
@OptIn(ExperimentalKatachiApi::class)
internal class NoArgContextView(
    internal val base: ArchitectureProcessContext<Unit>,
) : ArchitectureProcessNoArgContext, ArchitectureProcessContext<Unit> by base {
    // Both supertypes declare `args`, so it has to be overridden here; the deprecation is meant
    // for the implementer of `process`, not for this bridge.
    @Suppress("OVERRIDE_DEPRECATION")
    override val args: Unit get() = base.args

    override fun toString(): String = base.toString()
}
