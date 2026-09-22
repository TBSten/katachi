package me.tbsten.katachi.scan

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/**
 * One problem found by a check.
 *
 * The check returns these as a list rather than throwing, so that a later version can filter
 * them (a baseline), write them out (JSON / SARIF) or count them without any of it being a
 * rewrite. `assert()` is the thin layer on top that turns a non-empty list into a failure.
 *
 * A violation carries everything its report block needs and nothing else: the wording lives
 * in the report, not here. A violation declared outside katachi has no block of its own,
 * though, so [details] is the door it uses to carry values in instead.
 *
 * It sits next to the walk rather than next to `assert()` because the walk is what produces
 * one. Deciding that a file no role allows is an `UnexpectedFile` is the same decision as
 * deciding not to descend into the directory it sits in, so splitting the two would mean a
 * second vocabulary that mirrors this one line for line. What `me.tbsten.katachi.check` holds
 * instead is what a caller does with the list: word it, fail a test with it, ignore part of it.
 *
 * ## Example 1: inspect the violations after a failed check
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> { projectArchitecture.assert() }
 * failure.violations.filter { it.severity == Severity.Error }.map { it.path }
 * ```
 */
public interface Violation {
    /**
     * Which of the three problems this is.
     *
     * ## Example 1: group violations for a report, one block per kind
     * ```kt
     * projectArchitecture.validate().groupBy { it.kind }
     * ```
     */
    public val kind: ViolationKind

    /**
     * Whether this fails the check.
     *
     * ## Example 1: separate failing violations from warnings
     * ```kt
     * val (failures, warnings) = projectArchitecture.validate()
     *     .partition { it.severity == Severity.Error }
     * ```
     */
    public val severity: Severity

    /**
     * The path the block's first line names, relative to the project root and `/` separated.
     * Where something actually is for [ViolationKind.Unexpected], where it was declared to be
     * for [ViolationKind.Missing], and where the check gave up for [ViolationKind.Failed].
     *
     * ## Example 1: list where every violation was found
     * ```kt
     * projectArchitecture.validate().map { it.path }
     * ```
     */
    public val path: String

    /**
     * The `[...]` label of the block's first line, e.g. `UnexpectedFile`.
     *
     * ## Example 1: print each violation like its report block's first line
     * ```kt
     * projectArchitecture.validate().forEach { println("[${it.label}] ${it.path}") }
     * ```
     */
    public val label: String

    /**
     * What a report block states about this violation when katachi does not know its type,
     * one entry per line, in this order.
     *
     * katachi's own violations leave this empty: their blocks are written in `ViolationReport`,
     * because those blocks have shapes a flat list cannot hold — a nested list of nearby
     * locations, a copyable DSL fragment, a "How to fix" section whose contents depend on the
     * kind. That file stays the one place a reader looks to change how a report reads.
     *
     * A violation declared outside katachi has no block there, because the report cannot switch
     * on a type it has never seen. So it says what it knows, and it says it as **values**: one
     * label and one value per entry, one line each, no leading marker and no indentation of its
     * own.
     *
     * ## Example 1: word a violation an artifact of your own produces
     * ```kt
     * override val details: List<ViolationDetail>
     *     get() = listOf(ViolationDetail("Rule", ruleId), ViolationDetail("Line", "$line"))
     * ```
     */
    public val details: List<ViolationDetail> get() = emptyList()
}

/**
 * A place a role declared, offered as somewhere an unexpected file could move to.
 *
 * ## Example 1: read the nearby locations suggested for an unexpected file
 * ```kt
 * val unexpectedFile = projectArchitecture.validate().filterIsInstance<UnexpectedFile>().first()
 * unexpectedFile.nearby.map { it.directory }
 * ```
 */
public class NearbyLocation internal constructor(
    /**
     * The role that may put files there.
     *
     * ## Example 1: read which role may put files at a nearby location
     * ```kt
     * unexpectedFile.nearby.first().role.qualifiedName
     * ```
     */
    public val role: Role,
    /**
     * The directory, relative to the project root. Never empty, never holds a wildcard.
     *
     * ## Example 1: read the directory a nearby role's files may live in
     * ```kt
     * unexpectedFile.nearby.first().directory
     * ```
     */
    public val directory: String,
) {
    override fun toString(): String = "${role.qualifiedName} -> $directory"
}

/**
 * A file that exists although no role's layout allows it.
 *
 * ## Example 1: list every file nothing declared
 * ```kt
 * projectArchitecture.validate().filterIsInstance<UnexpectedFile>().map { it.path }
 * ```
 */
public class UnexpectedFile internal constructor(
    override val path: String,
    /**
     * Declared directories close to this file, nearest first. May be empty.
     *
     * ## Example 1: suggest where an unexpected file could move to
     * ```kt
     * projectArchitecture.validate().filterIsInstance<UnexpectedFile>().first().nearby
     * ```
     */
    public val nearby: List<NearbyLocation>,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Unexpected
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UnexpectedFile"
    override fun toString(): String = "[$label] $path"
}

/**
 * A directory that exists although no role's layout mentions it.
 *
 * Reported instead of everything below it. Nothing inside a directory no role knows about
 * has a role either, so listing two hundred files would bury the one thing to fix, which is
 * the directory itself. The check does not descend past one of these.
 *
 * ## Example 1: list every directory nothing declared
 * ```kt
 * projectArchitecture.validate().filterIsInstance<UnexpectedDirectory>().map { it.path }
 * ```
 */
public class UnexpectedDirectory internal constructor(
    override val path: String,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Unexpected
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UnexpectedDirectory"
    override fun toString(): String = "[$label] $path"
}

/**
 * A file a role declared, with nothing at that path.
 *
 * Only a declaration without a wildcard can end up here: a pattern describes a place that
 * fills up over time, and no matches is a normal state for one.
 *
 * ## Example 1: list every declared file with nothing at its path yet
 * ```kt
 * projectArchitecture.validate().filterIsInstance<MissingFile>().map { it.path }
 * ```
 */
public class MissingFile internal constructor(
    override val path: String,
    /**
     * The role that declared the file.
     *
     * ## Example 1: read which role declared a missing file
     * ```kt
     * projectArchitecture.validate().filterIsInstance<MissingFile>().first().role.qualifiedName
     * ```
     */
    public val role: Role,
    /**
     * Where in the `layout { }` block it was declared.
     *
     * ## Example 1: point back at where the missing file was declared
     * ```kt
     * projectArchitecture.validate().filterIsInstance<MissingFile>().first().declaredAt
     * ```
     */
    public val declaredAt: DeclarationSite,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Missing
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "MissingFile"
    override fun toString(): String = "[$label] $path"
}

/**
 * A file the check failed at, so nothing is known about it.
 *
 * The walk keeps going past one of these: the file that threw is usually unrelated to the
 * violations the reader came for, and losing the whole report over it would leave them with
 * nothing to fix. What was lost is exactly this one path, and saying so is what [cause] and
 * the count at the end of the report are for.
 *
 * ## Example 1: list the files a run could not look at
 * ```kt
 * projectArchitecture.validate().filterIsInstance<UncheckedFile>().map { it.path }
 * ```
 *
 * ## Example 2: read what went wrong before reporting it as a katachi bug
 * ```kt
 * projectArchitecture.validate().filterIsInstance<UncheckedFile>().forEach {
 *     println("${it.path}: ${it.cause}")
 * }
 * ```
 */
public class UncheckedFile internal constructor(
    override val path: String,
    /**
     * What was thrown while the file was being checked.
     *
     * ## Example 1: keep only the failures that came from the file system
     * ```kt
     * projectArchitecture.validate()
     *     .filterIsInstance<UncheckedFile>()
     *     .filter { it.cause is java.io.IOException }
     * ```
     */
    public val cause: Throwable,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Failed
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UncheckedFile"
    override fun toString(): String = "[$label] $path"
}

/**
 * What a directory was being read for when it threw, and therefore what was lost with it.
 *
 * Two values, and only two, because a directory is read twice by one run and the two readings
 * lose different things. The distinction is not cosmetic: saying "nothing below it was
 * checked" about a search failure would be a claim the run cannot back up, since the walk that
 * comes after it looks into that same directory perfectly well.
 *
 * ## Example 1: tell an unreadable directory from one whose modules went unfound
 * ```kt
 * projectArchitecture.validate().filterIsInstance<UncheckedDirectory>().map { it.reason } shouldBe
 *     listOf(UncheckedDirectoryReason.NotWalked)
 * ```
 */
public enum class UncheckedDirectoryReason {
    /**
     * The walk failed at the directory, so nothing below it was checked.
     *
     * ## Example 1: list the directories whose contents nothing looked at
     * ```kt
     * projectArchitecture.validate()
     *     .filterIsInstance<UncheckedDirectory>()
     *     .filter { it.reason == UncheckedDirectoryReason.NotWalked }
     *     .map { it.path }
     * ```
     */
    NotWalked,

    /**
     * The search for the project's Gradle modules failed at the directory, so a module key may
     * stand for fewer modules than the project has. What the walk itself found below it is
     * unaffected.
     *
     * ## Example 1: explain a `:feature:*` key that expanded to too few modules
     * ```kt
     * projectArchitecture.validate()
     *     .filterIsInstance<UncheckedDirectory>()
     *     .filter { it.reason == UncheckedDirectoryReason.ModulesNotDiscovered }
     *     .map { it.path }
     * ```
     */
    ModulesNotDiscovered,
}

/**
 * A directory the check failed at, so part of what it would have said is unknown.
 *
 * Reported instead of everything inside it, the way [UnexpectedDirectory] is: the run never
 * got the directory's contents, so there is one path to report and it is this one. Its
 * siblings are unaffected — and which of the two readings failed, which is what decides how
 * much was lost, is [reason].
 *
 * ## Example 1: list the directories a run could not look into
 * ```kt
 * projectArchitecture.validate().filterIsInstance<UncheckedDirectory>().map { it.path }
 * ```
 *
 * ## Example 2: read what went wrong before reporting it as a katachi bug
 * ```kt
 * projectArchitecture.validate().filterIsInstance<UncheckedDirectory>().forEach {
 *     println("${it.path}: ${it.cause}")
 * }
 * ```
 */
public class UncheckedDirectory internal constructor(
    override val path: String,
    /**
     * Which of the two readings of the directory failed. See [UncheckedDirectoryReason].
     *
     * ## Example 1: branch on how much a failed directory cost
     * ```kt
     * projectArchitecture.validate().filterIsInstance<UncheckedDirectory>().single().reason shouldBe
     *     UncheckedDirectoryReason.ModulesNotDiscovered
     * ```
     */
    public val reason: UncheckedDirectoryReason,
    /**
     * What was thrown while the directory was being checked.
     *
     * ## Example 1: keep only the failures that came from the file system
     * ```kt
     * projectArchitecture.validate()
     *     .filterIsInstance<UncheckedDirectory>()
     *     .filter { it.cause is java.io.IOException }
     * ```
     */
    public val cause: Throwable,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Failed
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UncheckedDirectory"
    override fun toString(): String = "[$label] $path"
}
