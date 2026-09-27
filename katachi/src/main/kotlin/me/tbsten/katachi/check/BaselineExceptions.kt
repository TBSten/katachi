package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/** How the messages below tell the reader to create or rebuild the file. */
private const val UPDATE_COMMAND: String = "./gradlew <module>:test -Dkatachi.baseline.update=true"

/**
 * The baseline file named by `baseline = baselineFile(...)` does not exist.
 *
 * An ordinary run does not treat a missing file as an empty one: a typo in the path would
 * otherwise turn every held-back violation into a new one, or worse, go unnoticed once the
 * project is clean.
 *
 * ## Example 1: tell a missing baseline apart from a failed check
 * ```kt
 * shouldThrow<KatachiBaselineNotFoundException> { projectArchitecture.assert() }.file
 * ```
 */
@ExperimentalKatachiApi
public class KatachiBaselineNotFoundException internal constructor(
    /** Where the file was looked for, as a `file:///...` URI. */
    public val file: String,
) : KatachiCheckException(
    message = """
        Baseline file $file does not exist.
        The definition names it with `baseline = baselineFile(...)`, so this run cannot tell which violations are held back.
        Create it by running the architecture test once with -Dkatachi.baseline.update=true, e.g.
          $UPDATE_COMMAND
        or correct the path passed to baselineFile(...) if the file is somewhere else.
    """.trimIndent(),
)

/**
 * The baseline file exists but is not what katachi writes.
 *
 * ## Example 1: read which line of the baseline file is broken
 * ```kt
 * val failure = shouldThrow<KatachiInvalidBaselineFileException> { projectArchitecture.assert() }
 * println("${failure.file}, line ${failure.line}: ${failure.problem}")
 * ```
 */
@ExperimentalKatachiApi
public class KatachiInvalidBaselineFileException internal constructor(
    /** The file, as a `file:///...` URI. */
    public val file: String,
    /** The line the problem was found on, counting from 1. */
    public val line: Int,
    /** What is wrong. */
    public val problem: Problem,
    /**
     * The value the message is built around: what the parser expected for [Problem.NotJson], the
     * field name for [Problem.MissingField], [Problem.DuplicateField] and [Problem.UnknownField],
     * what the value should have been for [Problem.WrongType], the count for
     * [Problem.CountBelowOne], the line of the first occurrence for [Problem.DuplicateEntry], and
     * the marker for [Problem.MergeConflict].
     */
    public val detail: String,
) : KatachiCheckException(
    message = buildString {
        appendLine(
            when (problem) {
                Problem.NotJson -> "Baseline file $file is not valid JSON at line $line: $detail."
                Problem.WrongType -> "Baseline file $file has a value of the wrong type at line $line: $detail."
                Problem.MissingField ->
                    "Baseline file $file is missing the field \"$detail\" in the object that starts at line $line."
                Problem.DuplicateField ->
                    "Baseline file $file has the field \"$detail\" twice in one object, the second time at line $line."
                Problem.UnknownField ->
                    "Baseline file $file has a field katachi does not know, \"$detail\", at line $line."
                Problem.CountBelowOne ->
                    "Baseline file $file has an entry with \"count\": $detail at line $line. " +
                        "A count is 1 or more; an entry that holds back nothing is left out."
                Problem.DuplicateEntry ->
                    "Baseline file $file lists the same entry twice, at line $detail and again at line $line."
                Problem.MergeConflict ->
                    "Baseline file $file has a merge conflict marker ($detail) at line $line."
            },
        )
        appendLine("katachi writes this file itself and does not expect it to be edited by hand.")
        if (problem == Problem.MergeConflict) {
            // Every entry is one line, so the usual resolution is to keep both sides.
            appendLine("Each entry is one line: keep the entries of both sides and delete the marker lines,")
            append("or take either side and rebuild the file with $UPDATE_COMMAND")
        } else {
            append("Fix the line, or delete the file and create it again with $UPDATE_COMMAND")
        }
    },
) {
    /**
     * What is wrong with a baseline file.
     *
     * ## Example 1: treat a file that is not JSON at all differently from a bad entry
     * ```kt
     * val broken = shouldThrow<KatachiInvalidBaselineFileException> { projectArchitecture.assert() }
     * val unreadable = broken.problem == KatachiInvalidBaselineFileException.Problem.NotJson
     * ```
     */
    public enum class Problem {
        /** The text is not JSON. */
        NotJson,

        /** A value is not of the type the format expects. */
        WrongType,

        /** A field the format requires is not there. */
        MissingField,

        /** One object has the same field twice. */
        DuplicateField,

        /** An object has a field the format does not have. */
        UnknownField,

        /** An entry's `count` is 0 or negative. */
        CountBelowOne,

        /** Two entries of one check have the same key. */
        DuplicateEntry,

        /** A merge left its `<<<<<<<`, `=======` or `>>>>>>>` line in the file. */
        MergeConflict,
    }
}

/**
 * The baseline file was written by a newer katachi, in a format this one cannot read.
 *
 * ## Example 1: read which version the file is in
 * ```kt
 * shouldThrow<KatachiUnsupportedBaselineVersionException> { projectArchitecture.assert() }.version
 * ```
 */
@ExperimentalKatachiApi
public class KatachiUnsupportedBaselineVersionException internal constructor(
    /** The file, as a `file:///...` URI. */
    public val file: String,
    /** The version the file says it is in. */
    public val version: Int,
    /** The newest version this katachi reads. */
    public val supportedVersion: Int,
) : KatachiCheckException(
    message = """
        Baseline file $file is in format version $version, but this katachi reads version $supportedVersion and older.
        It was written by a newer katachi. Upgrade katachi in this project to the version that wrote it, or later.
    """.trimIndent(),
)

/**
 * `-Dkatachi.baseline.update` or `-Dkatachi.baseline.prune` was given while the environment
 * variable `CI` is `true`.
 *
 * A build script that forgets such a setting would otherwise have CI rewrite the baseline on
 * every run and always pass. Update it locally and commit the change instead.
 *
 * ## Example 1: see which property was refused
 * ```kt
 * shouldThrow<KatachiBaselineUpdateInCiException> { projectArchitecture.assert() }.property
 * ```
 */
@ExperimentalKatachiApi
public class KatachiBaselineUpdateInCiException internal constructor(
    /** The system property that asked for the update, e.g. `katachi.baseline.update`. */
    public val property: String,
) : KatachiCheckException(
    message = """
        -D$property=true was given on CI (the environment variable CI is "true"), so the baseline was not changed.
        A baseline rewritten by CI on every run would let every new violation through.
        Run the architecture test with -D$property=true on your machine and commit the baseline file.
        To rebuild it from a CI job on purpose, unset CI in that job.
    """.trimIndent(),
)

/**
 * The baseline file could not be written.
 *
 * ## Example 1: read which file could not be written
 * ```kt
 * shouldThrow<KatachiBaselineWriteException> { projectArchitecture.assert() }.file
 * ```
 */
@ExperimentalKatachiApi
public class KatachiBaselineWriteException internal constructor(
    /** The file, as a `file:///...` URI. */
    public val file: String,
    cause: Throwable,
) : KatachiCheckException(
    message = """
        Could not write the baseline file $file: ${cause::class.qualifiedName}: ${cause.message?.lineSequence()?.firstOrNull().orEmpty()}
        Check that the directory exists and is writable, then run the update again.
    """.trimIndent(),
    cause = cause,
)

/**
 * One check class ran more than once in this test run against the same baseline file and found
 * different violations.
 *
 * The baseline files a check's entries under its class name alone, so each run answers for all
 * of them: it reports the entries it did not find as stale, and an update or a prune replaces
 * them with its own. Two runs finding different things would take turns deleting each other's
 * entries. This is thrown instead, by the later run, before it writes anything.
 *
 * It happens when two `assert(...)` calls pass the same check class configured differently, or
 * when two definitions with different layouts name the same baseline file.
 *
 * ## Example 1: see which check was run two ways
 * ```kt
 * shouldThrow<KatachiBaselineCheckConflictException> { otherArchitecture.assert() }.check
 * ```
 */
@ExperimentalKatachiApi
public class KatachiBaselineCheckConflictException internal constructor(
    /** The fully qualified class name the check's entries are filed under. */
    public val check: String,
    /** The baseline file, as a `file:///...` URI. */
    public val file: String,
    /** Where `baselineFile(...)` was called for the definition of the later run. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = """
        Check $check ran more than once in this test run against baseline file $file, declared at $declaredAt, and found different violations.
        The baseline files a check's entries under its class name alone, so each run would report the other runs' entries as stale, and an update or a prune by one would delete them.
        This happens when two assert(...) calls pass the check configured differently, or when two definitions with different layouts share one baseline file.
        Pass every configuration of the check to a single assert(...) call, or give each definition a baseline file of its own with baselineFile("...").
    """.trimIndent(),
)
