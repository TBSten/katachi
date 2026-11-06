package me.tbsten.katachi.diff

import java.io.File
import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.diff.internal.DEFAULT_FULL_IMPORTANCE_LINES
import me.tbsten.katachi.diff.internal.MarkdownFileNames
import me.tbsten.katachi.diff.internal.buildDiffReportModel
import me.tbsten.katachi.diff.internal.changedFiles
import me.tbsten.katachi.diff.internal.commitHistory
import me.tbsten.katachi.diff.internal.renderDiffHtml
import me.tbsten.katachi.diff.internal.renderDiffMarkdown
import me.tbsten.katachi.diff.internal.resolveDiffRange
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.internal.projectWalk

/**
 * Writes a report of which roles and groups a git diff touched, as HTML and Markdown.
 *
 * Every changed file is put under each role whose `layout { }` allows it, and every group and
 * role of the definition is listed, changed or not, so a reviewer sees at a glance which part of
 * the architecture a change lands in. Unchanged groups start folded. A file no role allows is
 * listed under "(no role)".
 *
 * Registered under the key `diff`, so a module that applies the Gradle plugin can run
 * `./gradlew katachiDiff` without registering anything.
 *
 * ## What is compared
 *
 * - `pr=` set: the pull request's base and head, resolved with the GitHub CLI and compared the
 *   way GitHub does (`git diff base...head`).
 * - otherwise `before` against `after` (`git diff before..after`). An empty `after` means the
 *   working tree, untracked files included.
 *
 * ## Example 1: report the current branch against main from code
 * ```kt
 * import me.tbsten.katachi.diff.DiffReport
 * import me.tbsten.katachi.processor.process
 *
 * projectArchitecture.process(DiffReport, DiffReport.Args(before = "origin/main", after = "HEAD")).getOrThrow()
 * ```
 *
 * ## Example 2: run it from the command line
 * ```sh
 * ./gradlew :architecture-test:katachiDiff --arg before=origin/main
 * ./gradlew :architecture-test:katachiDiff --arg pr=1234 --arg weights=Domain=2,Presentation.ViewModel=0.5
 * ```
 *
 * @see Args
 */
@ExperimentalKatachiApi
public object DiffReport : ArchitectureProcessor<DiffReport.Args, Unit> {
    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override fun process(context: ArchitectureProcessContext<Args>): Result<Unit> = runProcessorCatching {
        val args = context.args
        if (args.fullImportanceLines <= 0) throw KatachiInvalidFullImportanceLinesException(args.fullImportanceLines)
        val walk = context.projectWalk
        val root = walk.projectRoot
        val range = resolveDiffRange(root, before = args.before, after = args.after, pr = args.pr)
        context.log("Comparing ${range.label}")
        val changes = changedFiles(root, range)
        val model = buildDiffReportModel(
            architecture = context.architecture,
            entries = walk.resolvedEntries,
            range = range,
            changes = changes,
            weights = args.weights,
            maxFiles = args.maxFiles,
            fullImportanceLines = args.fullImportanceLines,
        )
        if (!args.markdown && !args.html) throw KatachiDiffReportNothingToWriteException()
        val outputs = buildList {
            if (args.html) add(File(args.htmlOutputFile) to renderDiffHtml(model, commitHistory(root, range)))
            if (args.markdown) {
                val summary = File(args.markdownOutputFile)
                val names = MarkdownFileNames(summary.name)
                val written = renderDiffMarkdown(model, names)
                // A listing an earlier run wrote and this one has nothing for would otherwise be read as current.
                for (name in names.all - written.keys) File(summary.absoluteFile.parentFile, name).delete()
                for ((name, content) in written) add(File(summary.absoluteFile.parentFile, name) to content)
            }
        }
        for ((file, content) in outputs) {
            try {
                file.absoluteFile.parentFile.mkdirs()
                file.writeText(content)
            } catch (cause: IOException) {
                throw KatachiDiffReportIoException(file.path, cause)
            }
            context.log("Wrote ${fileUri(absolutePathOf(file.path))} ")
        }
    }

    /**
     * What to compare, how to weigh it, and where the report goes.
     *
     * ## Example 1: weigh the domain double and the view models half
     * ```kt
     * import me.tbsten.katachi.diff.DiffReport
     *
     * DiffReport.Args(
     *     before = "origin/main",
     *     after = "HEAD",
     *     weights = listOf("Domain=2", "Presentation.ViewModel=0.5"),
     * )
     * ```
     *
     * ## Example 2: list every changed file of a large change
     * ```kt
     * import me.tbsten.katachi.diff.DiffReport
     *
     * // ./gradlew katachiDiff --arg before=origin/main --arg maxFiles=0
     * DiffReport.Args(before = "origin/main", maxFiles = 0)
     * ```
     *
     * ## Example 3: only the Markdown, into the module directory
     * ```kt
     * import me.tbsten.katachi.diff.DiffReport
     *
     * // ./gradlew katachiDiff --arg html=false --arg markdownOutputFile=diff-report.md
     * DiffReport.Args(html = false, markdownOutputFile = "diff-report.md")
     * ```
     *
     * ## Example 4: count a smaller change as full importance
     * ```kt
     * import me.tbsten.katachi.diff.DiffReport
     *
     * // ./gradlew katachiDiff --arg before=origin/main --arg fullImportanceLines=200
     * DiffReport.Args(before = "origin/main", fullImportanceLines = 200)
     * ```
     *
     * @constructor Takes the refs, the weights, the file limit, the importance scale and what to write where.
     */
    @Serializable
    public data class Args(
        /** The ref the change starts from. Ignored when [pr] is set. */
        val before: String = "HEAD",
        /** The ref the change ends at; empty for the working tree. Ignored when [pr] is set. */
        val after: String = "",
        /** A pull request number or URL, resolved with `gh pr view`. Wins over [before] and [after]. */
        val pr: String = "",
        /**
         * `<qualified group or role name>=<number>` entries that scale the heat of what they name.
         *
         * The most specific entry applies: a role's own over its group's. A role no entry reaches
         * takes the [diffWeight] written in the definition, and weighs 1 when there is none
         * either. An entry here wins over any `diffWeight`, even one written on a more specific
         * name. Given on the command line, these replace the build script's
         * `katachi { processors { diff { weights } } }` as a whole.
         */
        val weights: List<String> = emptyList(),
        /** Whether to write the Markdown report. */
        val markdown: Boolean = true,
        /**
         * Where the Markdown summary goes, relative to the directory the processor was started in.
         *
         * The file listings go next to it, named after it: `diff-report.md` puts them in
         * `diff-report.files-by-role.md` and `diff-report.files-in-no-role.md`.
         */
        val markdownOutputFile: String = DEFAULT_MARKDOWN_OUTPUT_FILE,
        /** Whether to write the HTML report. */
        val html: Boolean = true,
        /** Where the HTML report goes, relative to the directory the processor was started in. */
        val htmlOutputFile: String = DEFAULT_HTML_OUTPUT_FILE,
        /**
         * How many changed files the per-file listings show at most; 0 or less shows them all.
         *
         * Only the listings are cut: every group and role row, its heat, its line count and its
         * file count are still computed from all the changed files. The files kept are the ones
         * with the most diff lines after weighing, then by path, and the report says how many it
         * left out.
         */
        val maxFiles: Int = DEFAULT_MAX_FILES,
        /**
         * The diff lines, after weighing, at which a row's importance is full.
         *
         * Every group, role and file is measured against this one number rather than against the
         * largest row of the change, so a change of a few lines stays pale however small the rest
         * of the change is. A row past it is shown as full. Must be 1 or more.
         */
        val fullImportanceLines: Int = DEFAULT_FULL_IMPORTANCE_LINES,
    )
}

private const val DEFAULT_MARKDOWN_OUTPUT_FILE: String = "build/katachi/diff/diff-report.md"

private const val DEFAULT_HTML_OUTPUT_FILE: String = "build/katachi/diff/diff-report.html"

private const val DEFAULT_MAX_FILES: Int = 100
