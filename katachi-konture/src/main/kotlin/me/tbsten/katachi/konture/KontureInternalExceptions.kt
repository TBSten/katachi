package me.tbsten.katachi.konture

import me.tbsten.katachi.KatachiInternalException

/** Where a reader files a bug in this artifact. */
private const val ISSUES_URL: String = "https://github.com/TBSten/katachi/issues"

/**
 * Konture did not hand back every file katachi asked it to parse.
 *
 * The project graph is built by parsing an exact set of absolute file paths, one by one. If a
 * file is gone by the time it is parsed, or Konture reports it under a path that does not
 * compare equal — a separator, a symlink, a case difference — it drops out of the graph, the
 * rules see fewer declarations than they should, and the constraint passes for the wrong
 * reason. Counting is what makes that loud instead of silent.
 *
 * **This is katachi's bug, not the caller's.** Nothing a definition can say causes it, which
 * is why the base is [KatachiInternalException] and why the message asks for a report.
 *
 * ## Example 1: recognise it as a katachi bug rather than a failed rule
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureGraphIncompleteException>()
 * ```
 *
 * @property given how many files katachi asked Konture for.
 * @property visible how many of them came back in the graph.
 */
public class KatachiKontureGraphIncompleteException internal constructor(
    /**
     * How many files katachi asked Konture for.
     *
     * ## Example 1: read back how many files were expected
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureGraphIncompleteException>().given shouldBe 3
     * ```
     */
    public val given: Int,
    /**
     * How many of them came back in the graph.
     *
     * ## Example 1: read back how many files made it into the graph
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureGraphIncompleteException>().visible shouldBe 2
     * ```
     */
    public val visible: Int,
) : KatachiInternalException(
    message = buildString {
        appendLine(
            "Katachi asked Konture for $given " +
                "${if (given == 1) "file" else "files"} and got $visible back.",
        )
        appendLine(
            "The missing ones would simply be absent from the rules, so the constraint would " +
                "pass without ever looking at them. This is a bug in katachi-konture: the " +
                "absolute paths it builds and the ones Konture reports have stopped matching.",
        )
        append("Please report it at $ISSUES_URL.")
    },
)

/**
 * Konture reported a violation on a file this constraint does not cover, or on no file at all.
 *
 * The graph holds exactly the covered files, so every violation Konture produces should point
 * into one of them. One that points elsewhere — or carries no location — has no report block to
 * appear in: the first line of a block is a path a reader can open, and katachi never invents
 * one.
 *
 * **This is katachi's bug, not the caller's.** The graph is katachi's to build, so a violation
 * outside it means the graph and the covered files have drifted apart.
 *
 * ## Example 1: recognise it as a katachi bug rather than a failed rule
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureUnlocatableViolationException>()
 * ```
 *
 * @property ruleId Konture's id of the rule that produced the violation (`classes.rule` and so on).
 * @property subject what Konture said the violation was about, by name.
 * @property filePath the path Konture gave, or `null` when it gave none.
 */
public class KatachiKontureUnlocatableViolationException internal constructor(
    /**
     * Konture's id of the rule that produced the violation.
     *
     * ## Example 1: read back which rule kind produced the stray violation
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureUnlocatableViolationException>().ruleId shouldBe "classes.rule"
     * ```
     */
    public val ruleId: String,
    /**
     * What Konture said the violation was about, by name.
     *
     * ## Example 1: read back the subject of the stray violation
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureUnlocatableViolationException>().subject
     * ```
     */
    public val subject: String,
    /**
     * The path Konture gave for the violation, or `null` when it gave none.
     *
     * ## Example 1: see which path fell outside the covered files
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureUnlocatableViolationException>().filePath
     * ```
     */
    public val filePath: String?,
) : KatachiInternalException(
    message = buildString {
        appendLine(
            "Konture reported a violation of $ruleId on $subject at " +
                "${filePath ?: "no location"}, which is not a file this constraint covers.",
        )
        appendLine(
            "The first line of a report block has to be a path a reader can open, and katachi " +
                "cannot put a file it never asked about in a report. This is a bug in " +
                "katachi-konture: the graph it built and the files it covers have drifted apart.",
        )
        append("Please report it at $ISSUES_URL.")
    },
)

/**
 * Konture's parser threw on a file that exists.
 *
 * katachi hands Konture only files its own walk just found, so a parser failure here is either
 * a file that stopped being readable between the walk and the parse, or a Kotlin construct
 * Konture 1.0.1 does not understand. Neither is something the definition can be rewritten to
 * avoid, which is why the base is [KatachiInternalException].
 *
 * ## Example 1: recognise a parse failure as a bug to report
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureParseException>()
 * ```
 *
 * @property file the absolute path of the file Konture could not parse.
 */
public class KatachiKontureParseException internal constructor(
    /**
     * The absolute path of the file Konture could not parse.
     *
     * ## Example 1: open the file that would not parse
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureParseException>().file
     * ```
     */
    public val file: String,
    cause: Throwable,
) : KatachiInternalException(
    message = buildString {
        appendLine("Konture could not parse $file: ${cause.toString().lineSequence().first()}")
        appendLine(
            "katachi hands Konture only files its own walk just found, so this file exists " +
                "and Konture still refused it.",
        )
        append(
            "If the file compiles, this is a bug in katachi-konture (report it at $ISSUES_URL); " +
                "if Konture rejects valid Kotlin, report it to Konture at " +
                "https://github.com/baole/konture/issues.",
        )
    },
    cause = cause,
)
