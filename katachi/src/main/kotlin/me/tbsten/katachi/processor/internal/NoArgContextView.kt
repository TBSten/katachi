package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext

/**
 * The [ArchitectureProcessNoArgContext] an [me.tbsten.katachi.processor.ArchitectureProcessorNoArg]
 * is handed: [base] seen through the no-argument type.
 *
 * A named class rather than an anonymous object so that [projectWalk] and [withArgs] can see
 * through it to [base]. katachi's own checks are no-argument processors and read the walk off
 * the context they are given; behind an opaque wrapper they would find neither a real nor a
 * fake context.
 */
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
