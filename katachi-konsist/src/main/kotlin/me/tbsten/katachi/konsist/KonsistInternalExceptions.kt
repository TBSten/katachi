package me.tbsten.katachi.konsist

import me.tbsten.katachi.KatachiInternalException

/**
 * Konsist did not hand back every file katachi asked it to parse.
 *
 * The scope is built from absolute directory paths and then narrowed to an exact set of
 * absolute file paths. If one of those paths does not compare equal — a separator, a symlink,
 * a case difference — the file drops out of the scope, the block sees fewer declarations than
 * it should, and the constraint passes for the wrong reason. Counting is what makes that
 * loud instead of silent.
 *
 * **This is katachi's bug, not the caller's.** Nothing a definition can say causes it, which
 * is why the base is [KatachiInternalException] and why the message asks for a report.
 *
 * @property given how many files katachi asked Konsist for.
 * @property visible how many of them came back in the sliced scope.
 *
 * ## Example 1: recognise it as a katachi bug rather than a failed rule
 * ```kt
 * projectArchitecture.validate(KonsistCheck())
 *     .filterIsInstance<UncheckedConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKonsistScopeIncompleteException>()
 * ```
 */
public class KatachiKonsistScopeIncompleteException internal constructor(
    /**
     * How many files katachi asked Konsist for.
     *
     * ## Example 1: read back how many files were expected
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKonsistScopeIncompleteException>().given shouldBe 3
     * ```
     */
    public val given: Int,
    /**
     * How many of them came back in the sliced scope.
     *
     * ## Example 1: read back how many files survived the slice
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKonsistScopeIncompleteException>().visible shouldBe 2
     * ```
     */
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
