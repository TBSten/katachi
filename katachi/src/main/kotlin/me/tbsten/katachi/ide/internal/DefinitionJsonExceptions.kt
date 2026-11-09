package me.tbsten.katachi.ide.internal

import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.fileUri

/**
 * katachi could not write the definition JSON the IDE plugin reads.
 *
 * Nothing about the definition is wrong: the directory could not be created, the disk is full,
 * the path names a directory. The file that was there before, if any, is left as it was, since the
 * JSON is written next to it first and moved over it only once it is complete.
 *
 * ## Example 1: say where a failing run was writing
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.ide.internal.DescribeDefinition
 * import me.tbsten.katachi.ide.internal.KatachiDefinitionJsonIoException
 * import me.tbsten.katachi.processor.process
 *
 * val failure = shouldThrow<KatachiDefinitionJsonIoException> {
 *     projectArchitecture.process(DescribeDefinition, DescribeDefinition.Args(output = "/nowhere/definition.json")).getOrThrow()
 * }
 * println("could not write ${failure.output}")
 * ```
 *
 * @see DescribeDefinition
 */
internal class KatachiDefinitionJsonIoException(
    /** The file being written, exactly as `--arg output=` spelled it. */
    val output: String,
    cause: Throwable,
) : KatachiCheckException(
    message = buildString {
        appendLine("Cannot write the definition JSON to ${fileUri(absolutePathOf(output))}.")
        appendLine("The filesystem refused it: ${cause::class.simpleName}: ${cause.message}")
        append(
            "Check that --arg output= names a file this process may write. A relative path is " +
                "resolved against the directory the processor was started in, which under " +
                "Gradle is the module the task belongs to.",
        )
    },
    cause = cause,
)
