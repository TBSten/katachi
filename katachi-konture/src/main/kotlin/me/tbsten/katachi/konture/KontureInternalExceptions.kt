package me.tbsten.katachi.konture

import me.tbsten.katachi.KatachiInternalException

private const val ISSUES_URL: String = "https://github.com/TBSten/katachi/issues"

/**
 * Konture did not hand back every file katachi asked it to parse.
 *
 * Files missing from Konture's view would let the constraint pass without looking at them, so
 * the constraint is reported as unchecked instead.
 *
 * **This is a bug in katachi-konture, not in your definition.** Nothing you write causes it;
 * please report it at https://github.com/TBSten/katachi/issues.
 *
 * ## Example 1: recognise it as a katachi bug rather than a failed rule
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * val cause = failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureGraphIncompleteException>()
 * cause.given shouldBe 3
 * cause.visible shouldBe 2
 * ```
 *
 * @property given how many files katachi asked Konture for.
 * @property visible how many of them came back in the graph.
 */
public class KatachiKontureGraphIncompleteException internal constructor(
    /** How many files katachi asked Konture for. */
    public val given: Int,
    /** How many of them came back in the graph. */
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
 * Such a violation has no file to report it on, so the constraint is reported as unchecked instead.
 *
 * **This is a bug in katachi-konture, not in your definition.** Nothing you write causes it;
 * please report it at https://github.com/TBSten/katachi/issues.
 *
 * ## Example 1: recognise it as a katachi bug rather than a failed rule
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * val cause = failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureUnlocatableViolationException>()
 * cause.ruleId shouldBe "classes.rule"
 * ```
 *
 * @property ruleId Konture's id of the rule that produced the violation (`classes.rule` and so on).
 * @property subject what Konture said the violation was about, by name.
 * @property filePath the path Konture gave, or `null` when it gave none.
 */
public class KatachiKontureUnlocatableViolationException internal constructor(
    /** Konture's id of the rule that produced the violation. */
    public val ruleId: String,
    /** What Konture said the violation was about, by name. */
    public val subject: String,
    /** The path Konture gave for the violation, or `null` when it gave none. */
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
 * Konture does not understand. Neither is something the definition can be rewritten to
 * avoid, which is why the base is [KatachiInternalException].
 *
 * ## Example 1: recognise a parse failure as a bug to report
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * val cause = failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureParseException>()
 * cause.file
 * ```
 *
 * @property file the absolute path of the file Konture could not parse.
 */
public class KatachiKontureParseException internal constructor(
    /** The absolute path of the file Konture could not parse. */
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
