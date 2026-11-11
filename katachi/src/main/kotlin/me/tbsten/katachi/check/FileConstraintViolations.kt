package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason
import me.tbsten.katachi.dsl.Role

/**
 * A constraint said no about a file that exists.
 *
 * katachi knows which file a rejection was about, and optionally which declaration inside it.
 * The reasons, what the rule would have allowed, and how to fix it come from the constraint
 * ([FileConstraintFailure][me.tbsten.katachi.dsl.FileConstraintFailure]) and are printed only when it said them;
 * katachi never adds a "How to fix" of its own.
 *
 * One of these per rejected file per constraint. A constraint rejecting four classes in one file
 * produces four, each with its own [declaration][UnsatisfiedFileConstraint.declaration] and
 * [line][UnsatisfiedFileConstraint.line]; the same declaration rejected twice by one block, for
 * the same reasons, produces one.
 *
 * ## Example 1: list the files a constraint rejected
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UnsatisfiedFileConstraint>()
 *     .map { it.path } shouldBe listOf("core/domain/useCase/Helper.kt")
 *
 * val rejected = failure.violations.filterIsInstance<UnsatisfiedFileConstraint>().single()
 * println("${rejected.declaration} at line ${rejected.line}: ${rejected.reasons}")
 * val site = rejected.ruleDeclaredAt ?: rejected.declaredAt
 * ```
 */
public class UnsatisfiedFileConstraint internal constructor(
    /** The rejected file, project relative — one of the files the constraint was handed. */
    override val path: String,
    /** What inside the file was rejected, or `null` for a backend that answers per whole file. */
    public val declaration: String?,
    /**
     * Line [declaration][UnsatisfiedFileConstraint.declaration] starts at, 1-based, or `null` when
     * the backend could not say.
     */
    public val line: Int?,
    /** The role whose layout the constraint was written in. */
    public val role: Role,
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val constraintName: String?,
    /** The block's own resolved directory, or `null` when the constraint covers more than one. */
    public val layoutPath: String?,
    /** Where the constraint was written. */
    public val declaredAt: DeclarationSite,
    /** Why the constraint said no, one `<label>: <text>` line each, or empty when it did not say. */
    @property:ExperimentalKatachiApi
    public val reasons: List<FileConstraintReason> = emptyList(),
    /** What the constraint would have accepted instead, or `null` when it did not say. */
    @property:ExperimentalKatachiApi
    public val allowed: FileConstraintAllowedList? = null,
    /**
     * How to fix it, as the constraint put it. Empty when it did not say, and then the report has
     * no `How to fix:`.
     */
    @property:ExperimentalKatachiApi
    public val howToFix: List<FileConstraintFix> = emptyList(),
    /**
     * The line of the rule that said no, or `null` when the constraint did not name one.
     *
     * The report prints it as `Declared at:` instead of
     * [declaredAt][UnsatisfiedFileConstraint.declaredAt].
     */
    @property:ExperimentalKatachiApi
    public val ruleDeclaredAt: DeclarationSite? = null,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.FileConstraint
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UnsatisfiedFileConstraint"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}

/**
 * Why nothing is known about a constraint.
 *
 * Either nobody evaluated it, or evaluating it failed. A constraint that matches no files is not
 * reported at all.
 *
 * ## Example 1: tell a forgotten check apart from a broken rule
 * ```kt
 * violations.filterIsInstance<UncheckedFileConstraint>().map { it.reason } shouldBe
 *     listOf(UncheckedFileConstraintReason.NotEvaluated)
 * ```
 */
public enum class UncheckedFileConstraintReason {
    /**
     * No check handed to `assert(...)` evaluated it — most often `FileConstraintCheck()` was left
     * out of the arguments.
     */
    NotEvaluated,

    /** A check tried and threw, so what the block would have said is unknown. */
    Failed,
}

/**
 * A constraint nothing could answer for, so the run is a partial result.
 *
 * A constraint that was declared but never evaluated, most often because `FileConstraintCheck()`
 * was not passed to `assert(...)`, fails the run instead of leaving the test green.
 *
 * ## Example 1: notice that a run evaluated no constraints
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .map { it.reason } shouldBe listOf(UncheckedFileConstraintReason.NotEvaluated)
 * ```
 *
 * ## Example 2: read what stopped a broken rule
 * ```kt
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .filter { it.reason == UncheckedFileConstraintReason.Failed }
 *     .map { "${it.constraintName} (${it.declaredAt}): ${it.cause}" }
 * ```
 */
public class UncheckedFileConstraint internal constructor(
    /**
     * Where to start looking: the directory the constraint is anchored at.
     *
     * When the layout declared no directory at all, it is the file the constraint was written in.
     */
    override val path: String,
    /** Which of the two ways nothing is known. See [UncheckedFileConstraintReason]. */
    public val reason: UncheckedFileConstraintReason,
    /** The role whose layout the constraint was written in. */
    public val role: Role,
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val constraintName: String?,
    /** The block's own resolved directory, or `null` when the constraint covers more than one. */
    public val layoutPath: String?,
    /** Where the constraint was written. */
    public val declaredAt: DeclarationSite,
    /**
     * What stopped it, or `null` for [UncheckedFileConstraintReason.NotEvaluated] — where nothing
     * was tried, so nothing threw.
     */
    public val cause: Throwable?,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Failed
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UncheckedFileConstraint"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}
