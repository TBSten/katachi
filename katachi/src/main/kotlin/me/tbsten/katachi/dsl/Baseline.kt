package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * Where the baseline lives: the ledger of violations the project already had when the check
 * was adopted, held back so that only new ones fail.
 *
 * A violation is recorded by the check that found it, its label and its path — plus, for a
 * failed constraint, the role, the constraint and the declaration — and never by its line, so
 * editing the file around it does not turn it into a new violation. Moving or renaming the file
 * does: where a file sits is exactly what katachi checks. The same key may be recorded more than
 * once, with a count.
 *
 * It is written on the definition rather than passed to `assert()` so that every way of running
 * a check — the test, and a check registered as a processor — reads the same ledger. It holds
 * temporary debt only: something meant to stay is declared in `layout { }` instead, where it
 * gets a role and a reason.
 *
 * The file itself is a file of the project like any other, so a `layout { }` has to allow it.
 * An `[UnexpectedFile]` about it is always reported and never recorded.
 *
 * A check's entries are filed under its class name, so every run of one check class against
 * one baseline has to find the same thing: two `assert(...)` calls passing the same check
 * configured differently, or two definitions with different layouts naming one file, fail with
 * `KatachiBaselineCheckConflictException`.
 *
 * ## Example 1: hold back what is already there
 * ```kt
 * val projectArchitecture = architecture {
 *     baseline()                                // or baseline("config/katachi-baseline.json")
 *     "BaselineFile" {
 *         summary = "Violations the project had when katachi was adopted"
 *         layout { "katachi-baseline.json".file() }
 *     }
 * }
 * ```
 * ```sh
 * ./gradlew :architecture-test:test -Dkatachi.baseline.update=true   # record what is there now
 * ./gradlew :architecture-test:test -Dkatachi.baseline.prune=true    # drop what was fixed
 * ```
 *
 * @see ArchitectureScope.baseline
 * @see me.tbsten.katachi.check.StaleBaselineEntry
 */
@ExperimentalKatachiApi
public class Baseline internal constructor(
    /** The baseline file, relative to the project root and `/` separated. */
    public val path: String,
    /** Where `baseline(...)` was called; not part of what makes two baselines equal. */
    internal val declaredAt: DeclarationSite = DeclarationSite.Unknown,
) {
    /** Equal when both read the same [path][Baseline.path]. */
    override fun equals(other: Any?): Boolean = other is Baseline && other.path == path
    /** Hashes [path][Baseline.path] alone, to agree with [equals]. */
    override fun hashCode(): Int = path.hashCode()
    /** `Baseline(<path>)`. */
    override fun toString(): String = "Baseline($path)"
}

/**
 * The path given to `baseline(...)` is not a project-relative, `/` separated path.
 *
 * ## Example 1: an absolute path is refused while the definition is read
 * ```kt
 * shouldThrow<KatachiInvalidBaselinePathException> {
 *     architecture { baseline("/tmp/katachi-baseline.json") }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class KatachiInvalidBaselinePathException internal constructor(
    /** The path as it was written. */
    public val path: String,
    /** Where `baseline(...)` was called. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = """
        Invalid baseline path "$path" declared at $declaredAt.
        A baseline path is relative to the project root and separated by '/': not empty, no leading '/' or drive letter, no '\', no '.' or '..' segment, no '//' and no trailing '/'.
        Write it the way the file sits below the project root, e.g. baseline("katachi-baseline.json") or baseline("config/katachi-baseline.json").
    """.trimIndent(),
)
