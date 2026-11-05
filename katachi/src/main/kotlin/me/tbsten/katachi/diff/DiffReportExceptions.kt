package me.tbsten.katachi.diff

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.fileUri
import java.io.IOException

/**
 * A `git` command [DiffReport] needs did not succeed.
 *
 * Usually a ref that does not exist in this clone (a shallow CI checkout, a PR head that was never
 * fetched), or `git` itself missing from `PATH`.
 *
 * ## Example 1: tell a missing ref apart from a broken definition
 * ```kt
 * import me.tbsten.katachi.diff.DiffReport
 * import me.tbsten.katachi.diff.KatachiDiffGitException
 * import me.tbsten.katachi.processor.process
 *
 * val failure = projectArchitecture.process(DiffReport, DiffReport.Args(before = "no-such-ref"))
 *     .exceptionOrNull() as? KatachiDiffGitException
 * println(failure?.command)
 * ```
 *
 * @see DiffReport
 */
@ExperimentalKatachiApi
public class KatachiDiffGitException internal constructor(
    /** The command that failed, as it would be typed in a shell. */
    public val command: String,
    /** What `git` wrote to standard error, trimmed. Empty when it could not be started at all. */
    public val stderr: String,
) : KatachiCheckException(
    message = buildString {
        appendLine("`$command` failed, so the diff report could not be built.")
        if (stderr.isNotEmpty()) appendLine(stderr.lines().joinToString("\n") { "  $it" })
        append(
            "Check that both refs exist in this clone (`git fetch` them, or deepen a shallow " +
                "checkout with `git fetch --unshallow`), and that `git` is on PATH.",
        )
    },
)

/**
 * The pull request given as `--arg pr=` could not be resolved into two commits.
 *
 * [DiffReport] asks the GitHub CLI (`gh`) for the base and head of the pull request, so `gh`
 * has to be installed and signed in.
 *
 * ## Example 1: fall back to explicit refs when gh is unavailable
 * ```kt
 * import me.tbsten.katachi.diff.DiffReport
 * import me.tbsten.katachi.diff.KatachiDiffPullRequestException
 * import me.tbsten.katachi.processor.process
 *
 * val result = projectArchitecture.process(DiffReport, DiffReport.Args(pr = "1234"))
 * if (result.exceptionOrNull() is KatachiDiffPullRequestException) {
 *     projectArchitecture.process(DiffReport, DiffReport.Args(before = "origin/main", after = "HEAD"))
 * }
 * ```
 *
 * @see DiffReport
 */
@ExperimentalKatachiApi
public class KatachiDiffPullRequestException internal constructor(
    /** The value of `--arg pr=`, exactly as given. */
    public val pr: String,
    /** What `gh` wrote to standard error, trimmed. Empty when it could not be started at all. */
    public val stderr: String,
) : KatachiCheckException(
    message = buildString {
        appendLine("Pull request \"$pr\" could not be resolved with `gh pr view`.")
        if (stderr.isNotEmpty()) appendLine(stderr.lines().joinToString("\n") { "  $it" })
        append(
            "Install the GitHub CLI and run `gh auth login`, or pass the two commits directly " +
                "with `--arg before=<base> --arg after=<head>`.",
        )
    },
)

/**
 * A `--arg weights=` entry is not `<Group or Role>=<number>`, or names nothing in the definition.
 *
 * ## Example 1: a weight that names a role
 * ```kt
 * import me.tbsten.katachi.diff.DiffReport
 *
 * // ./gradlew katachiDiff --arg weights=Presentation.ViewModel=0.5,Domain=1.2
 * DiffReport.Args(weights = listOf("Presentation.ViewModel=0.5", "Domain=1.2"))
 * ```
 *
 * @see DiffReport.Args.weights
 */
@ExperimentalKatachiApi
public class KatachiInvalidDiffWeightException internal constructor(
    /** The entry as given. */
    public val entry: String,
    /** Qualified names of every group and role of the definition, the names an entry may use. */
    public val knownNames: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Invalid diff weight \"$entry\" passed as `--arg weights=`.")
        appendLine(
            "Each entry must be `<qualified group or role name>=<non-negative number>`, " +
                "for example `Presentation.ViewModel=0.5`.",
        )
        append("Known names: ${knownNames.joinToString(", ")}")
    },
)

/**
 * A `diffWeight` written on a group or a role of the definition is negative or not a number.
 *
 * ## Example 1: a weight written in the definition
 * ```kt
 * import me.tbsten.katachi.diff.diffWeight
 *
 * val projectArchitecture = architecture {
 *     "domain".group {
 *         diffWeight = 2.0
 *     }
 * }
 * ```
 *
 * @see DiffWeight
 */
@ExperimentalKatachiApi
public class KatachiInvalidDefinitionDiffWeightException internal constructor(
    /** The qualified name of the group or role the weight was written on. */
    public val qualifiedName: String,
    /** The value as written. */
    public val weight: Double,
    /** Where that group or role is declared. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Invalid diffWeight $weight on \"$qualifiedName\" declared at $declaredAt.")
        appendLine("A diff weight scales the changed lines, so it must be a number of 0 or more.")
        append("Write a non-negative number, such as `diffWeight = 2.0`, or remove it to weigh 1.")
    },
)

/**
 * [DiffReport] could not write one of its files.
 *
 * ## Example 1: say where the report was going
 * ```kt
 * import me.tbsten.katachi.diff.DiffReport
 * import me.tbsten.katachi.diff.KatachiDiffReportIoException
 * import me.tbsten.katachi.processor.process
 *
 * val failure = projectArchitecture.process(DiffReport, DiffReport.Args(htmlOutputFile = "/nowhere/diff.html"))
 *     .exceptionOrNull() as? KatachiDiffReportIoException
 * println(failure?.outputFile)
 * ```
 */
@ExperimentalKatachiApi
public class KatachiDiffReportIoException internal constructor(
    /** The file that could not be written, relative to where the processor was started unless absolute. */
    public val outputFile: String,
    cause: IOException,
) : KatachiCheckException(
    message = "The diff report could not be written to ${fileUri(absolutePathOf(outputFile))} : ${cause.message}\n" +
        "Check that the directory is writable, or pass another file with `--arg markdownOutputFile=` " +
        "or `--arg htmlOutputFile=`.",
    cause = cause,
)

/**
 * Both formats of [DiffReport] were turned off, so a run would write nothing.
 *
 * ## Example 1: turn off one format, not both
 * ```kt
 * import me.tbsten.katachi.diff.DiffReport
 *
 * // ./gradlew katachiDiff --arg html=false
 * DiffReport.Args(html = false)
 * ```
 */
@ExperimentalKatachiApi
public class KatachiDiffReportNothingToWriteException internal constructor() : KatachiDeclarationException(
    message = buildString {
        appendLine("The diff report was given markdown=false and html=false, so it has nothing to write.")
        appendLine("A run that writes no file looks like a success while producing nothing to read.")
        append(
            "Leave at least one of them on: remove `--arg markdown=false` or `--arg html=false`, or " +
                "`enable = false` in `katachi { processors { diff { markdown { } html { } } } }`.",
        )
    },
)
