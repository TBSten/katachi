package me.tbsten.katachi.docs

import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.docs.internal.compareDocuments
import me.tbsten.katachi.docs.internal.roleReferenceDocuments
import me.tbsten.katachi.docs.internal.writeDocuments
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor

/**
 * Writes the role reference of a definition to disk, as Markdown.
 *
 * Registered under the key `docs`, so a module that applies the Gradle plugin can run
 * `./gradlew katachiDocs` without registering anything.
 *
 * ## What it does and does not touch
 *
 * It reads the declarations and nothing else. File constraints are never run, no file of the
 * project is opened, and a repository full of violations still generates: documentation says
 * what the rules *are*.
 *
 * The one exception is a constraint that describes itself on its role's page
 * (`FileConstraint.documentSection`) with an example file, for a role that names no
 * `example(...)`: finding that file lists the project's files once. A project that cannot be
 * listed gives a stand-in example instead of a failure.
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
 * ./gradlew :architecture-test:katachiDocs
 *
 * # Compare instead of writing, and fail when the pages on disk are out of date.
 * ./gradlew :architecture-test:katachiDocs --arg mode=check
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
            val outputUri = fileUri(absolutePathOf(outputDir))
            when (context.args.mode) {
                DocumentationMode.Write -> {
                    context.log("Writing ${pages.size} pages to $outputUri")
                    writeDocuments(outputRoot, pages) { message -> context.log(message) }
                }

                DocumentationMode.Check -> {
                    context.log("Comparing ${pages.size} pages against $outputUri")
                    val difference = compareDocuments(outputRoot, pages)
                    if (!difference.isUpToDate) {
                        throw KatachiStaleDocumentationException(
                            outputDir = outputDir,
                            missing = difference.missing,
                            different = difference.different,
                            extra = difference.extra,
                        )
                    }
                    context.log("$outputUri is up to date.")
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
     * ./gradlew :architecture-test:katachiDocs \
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
     *
     * @constructor Takes where the pages go and whether to write them or only compare.
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
 * Both build exactly the same pages, so a check never disagrees with what a write produces.
 *
 * ## Example 1: pick the ending from the command line
 * ```kt
 * import me.tbsten.katachi.docs.DocumentationMode
 * import me.tbsten.katachi.docs.GenerateDocumentation
 *
 * // ./gradlew katachiDocs --arg mode=check
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

private const val DEFAULT_OUTPUT_DIR: String = "build/katachi/docs"
