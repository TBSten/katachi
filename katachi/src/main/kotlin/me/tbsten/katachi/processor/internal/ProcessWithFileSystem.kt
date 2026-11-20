package me.tbsten.katachi.processor.internal

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
 * For katachi's own modules only: `:katachi-konsist`'s specs run `DescribeDefinition` over a throwaway project this way.
 * A processor written elsewhere runs through [me.tbsten.katachi.processor.process] against a real checkout.
 *
 * ## Example 1: run a processor over a project directory that is not the working directory
 * ```kt
 * val summary = architecture
 *     .process(DescribeDefinition, DescribeDefinition.Args(output = "out.json"), RealFileSystem(root))
 *     .getOrThrow()
 * ```
 */
@InternalKatachiApi
public fun <Args, R> Architecture.process(
    processor: ArchitectureProcessor<Args, R>,
    args: Args,
    fileSystem: KatachiFileSystem,
): Result<R> = processor.process(RealArchitectureProcessContext(this, args, fileSystem))

internal fun <R> Architecture.process(
    fileSystem: KatachiFileSystem,
    block: (ArchitectureProcessContext<Unit>) -> R,
): R = block(RealArchitectureProcessContext(this, Unit, fileSystem))
