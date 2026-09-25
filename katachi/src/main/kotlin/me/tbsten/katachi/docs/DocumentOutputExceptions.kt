package me.tbsten.katachi.docs

import me.tbsten.katachi.KatachiCheckException

/**
 * The documentation on disk is not what the definition produces.
 *
 * Answered only by `--arg mode=check`, which compares instead of writing, and answered rather
 * than thrown: [GenerateDocumentation] returns it as `Result.failure`. The definition is fine
 * and katachi is fine -- what is out of date is the output directory of an earlier run, which
 * is why this is a check failure about the environment rather than a bad declaration: the fix
 * is to regenerate and commit, not to edit `architecture { }`.
 *
 * All three kinds are reported together. Fixing them is one command either way, and a reader
 * who sees only the first of five learns nothing about whether the rest are the same mistake.
 *
 * ## Example 1: list the stale pages a CI job found
 * ```kt
 * import me.tbsten.katachi.docs.DocumentationMode
 * import me.tbsten.katachi.docs.GenerateDocumentation
 * import me.tbsten.katachi.docs.KatachiStaleDocumentationException
 * import me.tbsten.katachi.processor.process
 *
 * val stale = projectArchitecture.process(
 *     GenerateDocumentation,
 *     GenerateDocumentation.Args(outputDir = "docs/architecture", mode = DocumentationMode.Check),
 * ).exceptionOrNull() as? KatachiStaleDocumentationException
 * println(stale?.different.orEmpty())
 * ```
 *
 * @see GenerateDocumentation
 */
public class KatachiStaleDocumentationException internal constructor(
    /** The directory that was compared, exactly as `--arg outputDir=` spelled it. */
    public val outputDir: String,
    /** Pages the definition produces that the directory does not hold at all. */
    public val missing: List<String>,
    /** Pages the directory holds with other contents. */
    public val different: List<String>,
    /** Pages the directory holds that this definition no longer produces. */
    public val extra: List<String>,
) : KatachiCheckException(
    message = buildString {
        appendLine(
            "${missing.size + different.size + extra.size} documentation pages under " +
                "\"$outputDir\" are not what this definition produces: " +
                "${missing.size} missing, ${different.size} different, ${extra.size} extra.",
        )
        for (path in missing) appendLine("  [missing]   $path")
        for (path in different) appendLine("  [different] $path")
        for (path in extra) appendLine("  [extra]     $path")
        appendLine(
            "Nothing was written, because this run was asked to compare rather than generate.",
        )
        append(
            "Run the same processor without `--arg mode=check` to bring the directory up to " +
                "date, then commit the result.",
        )
    },
)

/**
 * katachi could not read or write a file of the output directory.
 *
 * The one failure of documentation generation that is about neither the definition nor katachi:
 * the directory does not exist and cannot be created, the file is read only, the disk is full.
 * It names the output directory as well as the page, because the usual cause is an `outputDir`
 * that points somewhere other than where its author thought.
 *
 * ## Example 1: say which directory a failing run was writing into
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.docs.GenerateDocumentation
 * import me.tbsten.katachi.docs.KatachiDocumentIoException
 * import me.tbsten.katachi.processor.process
 *
 * val failure = shouldThrow<KatachiDocumentIoException> {
 *     projectArchitecture.process(
 *         GenerateDocumentation,
 *         GenerateDocumentation.Args(outputDir = "/nowhere/katachi/docs"),
 *     ).getOrThrow()
 * }
 * println("could not write ${failure.path} under ${failure.outputDir}")
 * ```
 *
 * @see GenerateDocumentation
 */
public class KatachiDocumentIoException internal constructor(
    /** The page being written or read, relative to [outputDir]. */
    public val path: String,
    /** The output directory, exactly as `--arg outputDir=` spelled it. */
    public val outputDir: String,
    cause: Throwable,
) : KatachiCheckException(
    message = buildString {
        appendLine("Cannot read or write the documentation page \"$path\" under \"$outputDir\".")
        appendLine("The filesystem refused it: ${cause::class.simpleName}: ${cause.message}")
        append(
            "Check that `--arg outputDir=` names a directory this process may write to. " +
                "A relative path is resolved against the directory the processor was started " +
                "in, which under Gradle is the module the task belongs to.",
        )
    },
    cause = cause,
)
