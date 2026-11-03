package me.tbsten.katachi.konsist

import me.tbsten.katachi.KatachiInternalException

/**
 * Konsist did not hand back every file katachi asked it to parse.
 *
 * Files missing from the scope would let the constraint pass without looking at them, so the
 * constraint is reported as unchecked instead.
 *
 * **This is a bug in katachi, not in your definition.** Nothing you write causes it; please
 * report it at https://github.com/TBSten/katachi/issues.
 *
 * ## Example 1: recognise it as a katachi bug rather than a failed rule
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * val cause = failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKonsistScopeIncompleteException>()
 * cause.given shouldBe 3
 * cause.visible shouldBe 2
 * ```
 *
 * @property given how many files katachi asked Konsist for.
 * @property visible how many of them came back in the scope.
 */
public class KatachiKonsistScopeIncompleteException internal constructor(
    /** How many files katachi asked Konsist for. */
    public val given: Int,
    /** How many of them came back in the scope. */
    public val visible: Int,
) : KatachiInternalException(
    message = buildString {
        appendLine(
            "Katachi asked Konsist for $given " +
                "${if (given == 1) "file" else "files"} and got $visible back.",
        )
        appendLine(
            "The missing ones would simply be absent from the block's queries, so the " +
                "constraint would pass without ever looking at them. This is a bug in katachi: " +
                "the absolute paths it builds and the ones Konsist reports have stopped " +
                "matching.",
        )
        append("Please report it at https://github.com/TBSten/katachi/issues.")
    },
)
