package me.tbsten.katachi.docs

import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.docs.internal.compareDocuments
import me.tbsten.katachi.docs.internal.roleReferenceDocuments
import me.tbsten.katachi.docs.internal.writeDocuments
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor

/**
 * Writes the role reference of a definition to disk, as Markdown.
 *
 * Registered under the key `docs`, so a module that applies the Gradle plugin can run
 * `./gradlew runKatachiProcessor --processor=docs` without registering anything.
 *
 * ## What it does and does not touch
 *
 * It reads the declarations and nothing else. `konsist { }` blocks are never run, no file of the
 * project is opened, and a repository full of violations still generates -- documentation says
 * what the rules *are*, and whether the repository keeps them is the check's answer, not this
 * one's.
 *
 * ## Why this object is the only part that does IO
 *
 * Building the pages is [roleReferenceDocuments], a function from a context to a map of path to
 * text. Everything that can be wrong about the output -- a heading, a table, a link that
 * resolves nowhere -- is decided there and pinned by specs comparing two strings. This object
 * adds the one thing a spec cannot compare and a pure function cannot do: it puts the map on a
 * disk. Keeping the split means [DocumentationMode.Check] is not a second implementation of
 * anything, only a second ending.
 *
 * ## What it answers
 *
 * `success` when the pages were written, or when [DocumentationMode.Check] found them up to date.
 * A stale directory under [DocumentationMode.Check] is a `failure` carrying
 * [KatachiStaleDocumentationException]. So is everything that stopped it from answering -- a
 * definition it cannot render, a file it cannot read or write: nothing is thrown out of
 * `process`.
 *
 * ## Example 1: generate into the default directory from code
 * ```kt
 * import me.tbsten.katachi.docs.GenerateDocumentation
 * import me.tbsten.katachi.processor.process
 *
 * projectArchitecture.process(GenerateDocumentation, GenerateDocumentation.Args()).getOrThrow()
 * ```
 *
 * ## Example 2: fail a test when the committed documentation is out of date
 * ```kt
 * import me.tbsten.katachi.docs.DocumentationMode
 * import me.tbsten.katachi.docs.GenerateDocumentation
 * import me.tbsten.katachi.processor.process
 *
 * class DocumentationSpec : FreeSpec({
 *     "docs/architecture が最新である" {
 *         projectArchitecture.process(
 *             GenerateDocumentation,
 *             GenerateDocumentation.Args(
 *                 outputDir = "docs/architecture",
 *                 mode = DocumentationMode.Check,
 *             ),
 *         ).getOrThrow()
 *     }
 * })
 * ```
 *
 * ## Example 3: run it from the command line
 * ```sh
 * # No registration needed: the Gradle plugin registers it under `docs`.
 * ./gradlew :architecture-test:runKatachiProcessor --processor=docs
 *
 * # Compare instead of writing, and fail when the pages on disk are out of date.
 * ./gradlew :architecture-test:runKatachiProcessor --processor=docs --arg mode=check
 * ```
 *
 * @see Args
 * @see DocumentationMode
 * @see KatachiStaleDocumentationException
 */
@ExperimentalKatachiApi
public object GenerateDocumentation : ArchitectureProcessor<GenerateDocumentation.Args, Unit> {
    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override fun process(context: ArchitectureProcessContext<Args>): Result<Unit> =
        runProcessorCatching {
            val outputDir = context.args.outputDir
            val pages = roleReferenceDocuments(context)
            val outputRoot = File(outputDir)
            when (context.args.mode) {
                DocumentationMode.Write -> {
                    context.log("Writing ${pages.size} pages to $outputDir")
                    writeDocuments(outputRoot, pages) { message -> context.log(message) }
                }

                DocumentationMode.Check -> {
                    context.log("Comparing ${pages.size} pages against $outputDir")
                    val difference = compareDocuments(outputRoot, pages)
                    if (!difference.isUpToDate) {
                        throw KatachiStaleDocumentationException(
                            outputDir = outputDir,
                            missing = difference.missing,
                            different = difference.different,
                            extra = difference.extra,
                        )
                    }
                    context.log("$outputDir is up to date.")
                }
            }
        }

    /**
     * Where the pages go, and whether they are written at all.
     *
     * The same two values can be given three ways: from code, as `--arg` on the command line, or
     * as the module's default in the Gradle plugin's `docs { }` block. A `--arg` wins over the
     * block, so one run can differ without the build file being touched.
     *
     * ## Example 1: from code
     * ```kt
     * import me.tbsten.katachi.docs.DocumentationMode
     * import me.tbsten.katachi.docs.GenerateDocumentation
     *
     * GenerateDocumentation.Args(outputDir = "docs/architecture", mode = DocumentationMode.Check)
     * ```
     *
     * ## Example 2: from the command line
     * ```sh
     * ./gradlew :architecture-test:runKatachiProcessor --processor=docs \
     *     --arg outputDir=docs/architecture --arg mode=check
     * ```
     *
     * ## Example 3: as the module's default
     * ```kts
     * // architecture-test/build.gradle.kts
     * import me.tbsten.katachi.gradle.KatachiDocsMode
     *
     * katachi {
     *     processors {
     *         docs {
     *             outputDir = layout.projectDirectory.dir("docs/architecture")
     *             mode = KatachiDocsMode.CHECK
     *         }
     *     }
     * }
     * ```
     */
    @Serializable
    public data class Args(
        /**
         * Where the pages go, relative to the directory the processor was started in -- under
         * Gradle, the directory of the module the task belongs to.
         *
         * The default is below `build/`, which keeps the output out of `gitTracked()` and so out
         * of the check: a generated page does not have to be declared in a `layout { }` to stop
         * being an unexpected file.
         */
        val outputDir: String = DEFAULT_OUTPUT_DIR,
        /** Whether to write the pages or to compare them against what is already there. */
        val mode: DocumentationMode = DocumentationMode.Write,
    )
}

/**
 * Whether [GenerateDocumentation] writes its pages or compares them.
 *
 * Two endings of one run rather than two processors, because everything before the ending is
 * the same function: a second processor would have to build the same pages the same way, and
 * "the check and the generator disagree" is the one failure neither of them could report.
 *
 * ## Example 1: pick the ending from the command line
 * ```kt
 * import me.tbsten.katachi.docs.DocumentationMode
 * import me.tbsten.katachi.docs.GenerateDocumentation
 *
 * // ./gradlew runKatachiProcessor --processor=docs --arg mode=check
 * GenerateDocumentation.Args(mode = DocumentationMode.Check)
 * ```
 */
@ExperimentalKatachiApi
@Serializable
public enum class DocumentationMode {
    /** Make the output directory hold exactly the pages this definition produces. */
    @SerialName("write")
    Write,

    /**
     * Change nothing, and fail when the output directory is not already what a write would
     * leave behind.
     */
    @SerialName("check")
    Check,
}

/**
 * Where the pages go when `--arg outputDir=` is not given.
 *
 * `docs` is the registry key the plugin registers this processor under, so the path a user sees
 * in `build/` and the word they type after `--processor=` are the same one.
 */
private const val DEFAULT_OUTPUT_DIR: String = "build/katachi/docs"
