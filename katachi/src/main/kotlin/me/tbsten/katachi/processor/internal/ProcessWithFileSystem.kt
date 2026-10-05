package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor

/**
 * [me.tbsten.katachi.processor.process] against [fileSystem], which is how katachi's own specs run a processor against a
 * tree that only exists in memory. Same deferral: [fileSystem] is untouched unless the
 * processor asks for files.
 *
 * Internal, so this door is katachi's own. [KatachiFileSystem] itself is
 * `@ExperimentalKatachiApi`, and the one public entry that runs a processor against one is for
 * katachi's own modules, so a processor written elsewhere tests against a real checkout instead.
 */
internal fun <R> Architecture.process(
    processor: ArchitectureProcessor<Unit, R>,
    fileSystem: KatachiFileSystem,
): Result<R> = process(processor, Unit, fileSystem)

/**
 * [me.tbsten.katachi.processor.process] with arguments, against [fileSystem].
 *
 * Public, but only for katachi's own modules: `:katachi-konsist`'s specs run `DescribeDefinition`
 * over a throwaway project on the real file system, since a `ktFileContent { }` reads real files.
 *
 * ## Example 1: run a processor over a project directory that is not the working directory
 * ```kt
 * val summary = definition.process(DescribeDefinition, DescribeDefinition.Args(output = "out.json"), RealFileSystem(root))
 * ```
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun <Args, R> Architecture.process(
    processor: ArchitectureProcessor<Args, R>,
    args: Args,
    fileSystem: KatachiFileSystem,
): Result<R> = processor.process(RealArchitectureProcessContext(this, args, fileSystem))

/**
 * [me.tbsten.katachi.processor.process] against [fileSystem] with the processor written inline. The file system comes first
 * so that the block stays a trailing lambda.
 */
internal fun <R> Architecture.process(
    fileSystem: KatachiFileSystem,
    block: (ArchitectureProcessContext<Unit>) -> R,
): R = block(RealArchitectureProcessContext(this, Unit, fileSystem))
