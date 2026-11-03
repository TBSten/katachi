package me.tbsten.katachi.docs

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.displayExistingPath
import me.tbsten.katachi.internal.fileUri

/**
 * The documentation on disk is not what the definition produces.
 *
 * Only [DocumentationMode.Check] (`--arg mode=check`) produces it, and [GenerateDocumentation]
 * returns it as `Result.failure` rather than throwing. The fix is to regenerate and commit, not
 * to edit `architecture { }`. Missing, different and extra pages are reported together.
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
@ExperimentalKatachiApi
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
        val root = absolutePathOf(outputDir)
        appendLine(
            "${missing.size + different.size + extra.size} documentation pages under " +
                "${fileUri(root)} are not what this definition produces: " +
                "${missing.size} missing, ${different.size} different, ${extra.size} extra.",
        )
        for (path in missing) appendLine("  [missing]   ${displayExistingPath(root, path)}")
        for (path in different) appendLine("  [different] ${displayExistingPath(root, path)}")
        for (path in extra) appendLine("  [extra]     ${displayExistingPath(root, path)}")
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
 * For example the directory cannot be created, the file is read only, or the disk is full. The
 * usual cause is an `outputDir` that points somewhere other than intended.
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
@ExperimentalKatachiApi
public class KatachiDocumentIoException internal constructor(
    /**
     * The page being written or read, relative to
     * [outputDir][KatachiDocumentIoException.outputDir].
     */
    public val path: String,
    /** The output directory, exactly as `--arg outputDir=` spelled it. */
    public val outputDir: String,
    cause: Throwable,
) : KatachiCheckException(
    message = buildString {
        val root = absolutePathOf(outputDir)
        appendLine("Cannot read or write the documentation page ${displayExistingPath(root, path)} under ${fileUri(root)}.")
        appendLine("The filesystem refused it: ${cause::class.simpleName}: ${cause.message}")
        append(
            "Check that `--arg outputDir=` names a directory this process may write to. " +
                "A relative path is resolved against the directory the processor was started " +
                "in, which under Gradle is the module the task belongs to.",
        )
    },
    cause = cause,
)
