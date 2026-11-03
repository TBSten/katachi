package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor

internal fun <R> Architecture.process(
    processor: ArchitectureProcessor<Unit, R>,
    fileSystem: KatachiFileSystem,
): Result<R> = process(processor, Unit, fileSystem)

/**
 * [me.tbsten.katachi.processor.process] with arguments, reading the project through [fileSystem] instead of the real one.
 *
 * ## Example 1: run a processor against a project given by your own file system
 * ```kt
 * val count = projectArchitecture
 *     .process(CountRoles, CountRoles.Args(prefix = "Get"), myFileSystem)
 *     .getOrThrow()
 * ```
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun <Args, R> Architecture.process(
    processor: ArchitectureProcessor<Args, R>,
    args: Args,
    fileSystem: KatachiFileSystem,
): Result<R> = processor.process(RealArchitectureProcessContext(this, args, fileSystem))

internal fun <R> Architecture.process(
    fileSystem: KatachiFileSystem,
    block: (ArchitectureProcessContext<Unit>) -> R,
): R = block(RealArchitectureProcessContext(this, Unit, fileSystem))
