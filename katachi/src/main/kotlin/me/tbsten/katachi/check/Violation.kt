package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/**
 * One problem found by a check.
 *
 * Checks return these as a list, so they can be filtered, counted or written out; `assert()`
 * turns a list holding an error into a test failure. A check of your own implements this
 * interface, and uses [details] to say what its report block should show.
 *
 * ## Example 1: inspect the violations after a failed check
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> { projectArchitecture.assert() }
 * failure.violations.filter { it.severity == Severity.Error }.map { it.path }
 * failure.violations.groupBy { it.kind }
 * failure.violations.forEach { println("[${it.label}] ${it.path}") }
 * ```
 */
public interface Violation {
    /** Which kind of problem this is. */
    public val kind: ViolationKind

    /** Whether this fails the check. */
    public val severity: Severity

    /**
     * The path the block's first line names, relative to the project root and `/` separated.
     *
     * Where something actually is for [ViolationKind.Unexpected], where it was declared to be
     * for [ViolationKind.Missing], and where the check gave up for [ViolationKind.Failed].
     */
    public val path: String

    /**
     * The `[...]` label of the block's first line, e.g. `UnexpectedFile`.
     *
     * `[label] path` is the report block's first line with the path left relative to the project
     * root; the report itself prints that path as a `file:///...` URI.
     */
    public val label: String

    /**
     * What the report block of a violation you declared yourself states, one entry per line.
     *
     * katachi's own violations leave this empty; their blocks are worded by katachi. Each entry is
     * one label and one value, printed on one line in this order.
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
 * Wildcards in the declared place (`feature` / `*` / `src/main/kotlin`) are filled in from the
 * unexpected file's own path, so the directory is one the file could actually be moved to.
 *
 * ## Example 1: read the nearby locations suggested for an unexpected file
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * val unexpectedFile = failure.violations.filterIsInstance<UnexpectedFile>().first()
 * unexpectedFile.nearby.map { "${it.role.qualifiedName}: ${it.directory} ${it.filePattern ?: "(any name)"}" }
 * ```
 */
public class NearbyLocation internal constructor(
    /** The role that may put files there. */
    public val role: Role,
    /**
     * The directory, relative to the project root, with the file's own path filled into every
     * wildcard. Never empty, never holds a wildcard.
     */
    public val directory: String,
    /**
     * The file name pattern the role declared there (`*Screen.kt`), or `null` when the role
     * wrote `anyFile()`.
     */
    public val filePattern: String?,
) {
    /** `<role> -> <directory>/<filePattern>`, for a log line or a test failure message. */
    override fun toString(): String =
        "${role.qualifiedName} -> $directory" + (filePattern?.let { "/$it" } ?: "")
}

/**
 * A file that exists although no role's layout allows it.
 *
 * ## Example 1: list every file nothing declared
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<UnexpectedFile>().map { it.path }
 * failure.violations.filterIsInstance<UnexpectedFile>().first().nearby
 * ```
 */
public class UnexpectedFile internal constructor(
    override val path: String,
    /** Declared directories close to this file, nearest first. May be empty. */
    public val nearby: List<NearbyLocation>,
    internal val suggestedRole: String? = null,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Unexpected
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UnexpectedFile"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}

/**
 * A directory that exists although no role's layout mentions it.
 *
 * Reported instead of everything below it: the check does not descend into it, so the one thing
 * to fix is not buried under every file inside.
 *
 * ## Example 1: list every directory nothing declared
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<UnexpectedDirectory>().map { it.path }
 * ```
 */
public class UnexpectedDirectory internal constructor(
    override val path: String,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Unexpected
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UnexpectedDirectory"
    /** `[<label>] <path>`, for a log line or a test failure message. */
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
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<MissingFile>()
 *     .map { "${it.path}, declared by ${it.role.qualifiedName} at ${it.declaredAt}" }
 * ```
 */
public class MissingFile internal constructor(
    override val path: String,
    /** The role that declared the file. */
    public val role: Role,
    /** Where in the `layout { }` block it was declared. */
    public val declaredAt: DeclarationSite,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Missing
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "MissingFile"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}

/**
 * A file the check failed at, so nothing is known about it.
 *
 * The check keeps going past one of these, so only this one path is lost; the rest of the
 * report is still complete.
 *
 * ## Example 1: list the files a run could not look at
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<UncheckedFile>().map { it.path }
 * ```
 *
 * ## Example 2: read what went wrong before reporting it as a katachi bug
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<UncheckedFile>().forEach {
 *     println("${it.path}: ${it.cause}")
 * }
 * ```
 */
public class UncheckedFile internal constructor(
    override val path: String,
    /** What was thrown while the file was being checked. */
    public val cause: Throwable,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Failed
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UncheckedFile"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}

/**
 * What a directory was being read for when it threw, and therefore what was lost with it.
 *
 * A run reads a directory twice, once to find the Gradle modules and once to check the layout,
 * and the two failures lose different things.
 *
 * ## Example 1: tell an unreadable directory from one whose modules went unfound
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<UncheckedDirectory>().map { it.reason } shouldBe
 *     listOf(UncheckedDirectoryReason.NotWalked)
 * ```
 */
public enum class UncheckedDirectoryReason {
    /** The walk failed at the directory, so nothing below it was checked. */
    NotWalked,

    /**
     * The search for Gradle modules failed at the directory, so a module wildcard may match fewer
     * modules than the project has.
     *
     * What the layout check found below it is unaffected.
     */
    ModulesNotDiscovered,
}

/**
 * A directory the check failed at, so part of what it would have said is unknown.
 *
 * Reported instead of everything inside it, the way [UnexpectedDirectory] is; its siblings are
 * unaffected. [reason][UncheckedDirectory.reason] says how much was lost.
 *
 * ## Example 1: list the directories a run could not look into
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<UncheckedDirectory>().map { it.path }
 * ```
 *
 * ## Example 2: read what went wrong before reporting it as a katachi bug
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<UncheckedDirectory>().forEach {
 *     println("${it.path} (${it.reason}): ${it.cause}")
 * }
 * ```
 */
public class UncheckedDirectory internal constructor(
    override val path: String,
    /** Which of the two readings of the directory failed. See [UncheckedDirectoryReason]. */
    public val reason: UncheckedDirectoryReason,
    /** What was thrown while the directory was being checked. */
    public val cause: Throwable,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Failed
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UncheckedDirectory"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}
