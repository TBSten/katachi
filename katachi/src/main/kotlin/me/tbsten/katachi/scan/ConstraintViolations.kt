package me.tbsten.katachi.scan

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/**
 * A constraint said no about a file that exists.
 *
 * The block a `constraint { }` — or a backend's `konsist { }` — holds is an arbitrary Kotlin
 * expression, so katachi knows only two things about a rejection: which file it was about, and
 * optionally which declaration inside it. That is deliberately all a block of this kind
 * prints: there is no "How to fix", because what would satisfy the rule is the rule's own
 * business and inventing advice about it would be inventing a claim.
 *
 * One of these per rejected file per constraint. A constraint rejecting four classes in one
 * file produces four, each with its own [declaration] and [line]; the same declaration
 * rejected twice by one block produces one.
 *
 * ## Example 1: list the files a constraint rejected
 * ```kt
 * projectArchitecture.validate(ConstraintCheck())
 *     .filterIsInstance<UnsatisfiedConstraint>()
 *     .map { it.path } shouldBe listOf("core/domain/useCase/Helper.kt")
 * ```
 */
public class UnsatisfiedConstraint internal constructor(
    /**
     * The rejected file, project relative — one of the files the constraint was handed.
     *
     * ## Example 1: open the file a rule rejected
     * ```kt
     * violations.filterIsInstance<UnsatisfiedConstraint>().map { it.path }
     * ```
     */
    override val path: String,
    /**
     * What inside the file was rejected, or `null` for a backend that answers per whole file.
     *
     * ## Example 1: name the class a rule rejected
     * ```kt
     * violations.filterIsInstance<UnsatisfiedConstraint>().single().declaration shouldBe "Helper"
     * ```
     */
    public val declaration: String?,
    /**
     * Line [declaration] starts at, 1-based, or `null` when the backend could not say.
     *
     * ## Example 1: jump to the rejected declaration
     * ```kt
     * violations.filterIsInstance<UnsatisfiedConstraint>().single().line shouldBe 12
     * ```
     */
    public val line: Int?,
    /**
     * The role whose layout the constraint was written in.
     *
     * ## Example 1: group rejections by role
     * ```kt
     * violations.filterIsInstance<UnsatisfiedConstraint>().groupBy { it.role.qualifiedName }
     * ```
     */
    public val role: Role,
    /**
     * What the constraint was called, or `null` when it was declared without a name.
     *
     * ## Example 1: tell two rules of one role apart
     * ```kt
     * violations.filterIsInstance<UnsatisfiedConstraint>().map { it.constraintName }
     * ```
     */
    public val constraintName: String?,
    /**
     * The block's own resolved directory, or `null` when the constraint covers more than one.
     *
     * ## Example 1: say which module a wildcard constraint was evaluated for
     * ```kt
     * violations.filterIsInstance<UnsatisfiedConstraint>().single().layoutPath shouldBe "feature/cart"
     * ```
     */
    public val layoutPath: String?,
    /**
     * Where the constraint was written.
     *
     * ## Example 1: point back at the line that declared the rule
     * ```kt
     * violations.filterIsInstance<UnsatisfiedConstraint>().single().declaredAt.fileName shouldBe
     *     "ProjectArchitecture.kt"
     * ```
     */
    public val declaredAt: DeclarationSite,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Constraint
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UnsatisfiedConstraint"
    override fun toString(): String = "[$label] $path"
}

/**
 * Why nothing is known about a constraint.
 *
 * Two values, and only two: either nobody evaluated it, or evaluating it failed. "It matched
 * no files" is deliberately absent — a declaration that currently matches nothing is how a
 * place waiting to fill up looks, and the layout check already treats it as normal.
 *
 * ## Example 1: tell a forgotten check apart from a broken rule
 * ```kt
 * violations.filterIsInstance<UncheckedConstraint>().map { it.reason } shouldBe
 *     listOf(UncheckedConstraintReason.NotEvaluated)
 * ```
 */
public enum class UncheckedConstraintReason {
    /**
     * No check handed to `assert(...)` evaluated it — most often `ConstraintCheck()` was left
     * out of the arguments.
     *
     * ## Example 1: catch the run that forgot to evaluate its constraints
     * ```kt
     * violations.filterIsInstance<UncheckedConstraint>()
     *     .filter { it.reason == UncheckedConstraintReason.NotEvaluated }
     * ```
     */
    NotEvaluated,

    /**
     * A check tried and threw, so what the block would have said is unknown.
     *
     * ## Example 1: read what stopped a constraint
     * ```kt
     * violations.filterIsInstance<UncheckedConstraint>()
     *     .filter { it.reason == UncheckedConstraintReason.Failed }
     *     .map { it.cause }
     * ```
     */
    Failed,
}

/**
 * A constraint nothing could answer for, so the run is a partial result.
 *
 * This is the violation that keeps `konsist { }` from being quietly decorative. A constraint
 * declared and never evaluated — because `ConstraintCheck()` was not passed to `assert(...)` —
 * is the worst way this library can break: rules exist, code breaks them, the test is green.
 * So a constraint nobody took responsibility for is itself a violation.
 *
 * ## Example 1: notice that a run evaluated no constraints
 * ```kt
 * projectArchitecture.validate()
 *     .filterIsInstance<UncheckedConstraint>()
 *     .map { it.reason } shouldBe listOf(UncheckedConstraintReason.NotEvaluated)
 * ```
 */
public class UncheckedConstraint internal constructor(
    /**
     * Where to start looking: the directory the constraint is anchored at, or — when the
     * layout declared no directory at all — the file the constraint was written in.
     *
     * ## Example 1: read the path a report opens the block with
     * ```kt
     * violations.filterIsInstance<UncheckedConstraint>().single().path shouldBe "core/domain/useCase"
     * ```
     */
    override val path: String,
    /**
     * Which of the two ways nothing is known. See [UncheckedConstraintReason].
     *
     * ## Example 1: branch on why a constraint went unanswered
     * ```kt
     * violations.filterIsInstance<UncheckedConstraint>().single().reason shouldBe
     *     UncheckedConstraintReason.Failed
     * ```
     */
    public val reason: UncheckedConstraintReason,
    /**
     * The role whose layout the constraint was written in.
     *
     * ## Example 1: say which role lost a rule
     * ```kt
     * violations.filterIsInstance<UncheckedConstraint>().single().role.qualifiedName shouldBe
     *     "domain/UseCase"
     * ```
     */
    public val role: Role,
    /**
     * What the constraint was called, or `null` when it was declared without a name.
     *
     * ## Example 1: list the rules a run could not answer for
     * ```kt
     * violations.filterIsInstance<UncheckedConstraint>().mapNotNull { it.constraintName }
     * ```
     */
    public val constraintName: String?,
    /**
     * The block's own resolved directory, or `null` when the constraint covers more than one.
     *
     * ## Example 1: say which module the unanswered rule belonged to
     * ```kt
     * violations.filterIsInstance<UncheckedConstraint>().single().layoutPath shouldBe "feature/home"
     * ```
     */
    public val layoutPath: String?,
    /**
     * Where the constraint was written.
     *
     * ## Example 1: jump to the rule to delete or to start evaluating
     * ```kt
     * violations.filterIsInstance<UncheckedConstraint>().single().declaredAt.lineNumber
     * ```
     */
    public val declaredAt: DeclarationSite,
    /**
     * What stopped it, or `null` for [UncheckedConstraintReason.NotEvaluated] — where nothing
     * was tried, so nothing threw.
     *
     * ## Example 1: read the failure behind a broken rule
     * ```kt
     * violations.filterIsInstance<UncheckedConstraint>().single().cause.shouldNotBeNull()
     * ```
     */
    public val cause: Throwable?,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Failed
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UncheckedConstraint"
    override fun toString(): String = "[$label] $path"
}
