package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.ConstraintFailure
import me.tbsten.katachi.dsl.ConstraintSubject
import me.tbsten.katachi.dsl.DeclaredConstraint
import me.tbsten.katachi.dsl.reportPath
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ProjectModel
import me.tbsten.katachi.scan.UncheckedConstraint
import me.tbsten.katachi.scan.UncheckedConstraintReason
import me.tbsten.katachi.scan.UnsatisfiedConstraint
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.scan.catching

/**
 * The check that evaluates `constraint { }` blocks — the `konsist { }` ones included.
 *
 * It is not run unless it is asked for. `assert()` walks the tree and checks the layout;
 * `assert(ConstraintCheck())` does that *and* runs the constraints. Making it explicit is
 * what lets a definition holding rules nobody evaluated be reported rather than pass quietly:
 * a constraint no check took responsibility for comes back as
 * [UncheckedConstraintReason.NotEvaluated].
 *
 * Passing it twice changes nothing — the second instance finds every constraint already
 * answered for and returns nothing, so no violation is counted twice.
 *
 * ## Example 1: the one line a project adds to evaluate its constraints
 * ```kt
 * @OptIn(ExperimentalKatachiApi::class)
 * class ProjectArchitectureTest {
 *     @Test
 *     fun `構成が allow list に従っている`() = projectArchitecture.assert(ConstraintCheck())
 * }
 * ```
 *
 * ## Example 2: read what the constraints found without failing the test
 * ```kt
 * projectArchitecture.validate(ConstraintCheck())
 *     .filterIsInstance<UnsatisfiedConstraint>()
 *     .map { it.path } shouldBe emptyList()
 * ```
 */
@ExperimentalKatachiApi
public class ConstraintCheck : ArchitectureProcessor<List<Violation>> {
    /**
     * Evaluates every constraint of [model] that nothing has evaluated yet.
     *
     * ## Example 1: run it as one check among others on a single walk
     * ```kt
     * projectArchitecture.validate(ConstraintCheck(), TodoCheck())
     * ```
     */
    override fun process(model: ProjectModel): List<Violation> = model.declaredConstraints
        // Handed the same constraint twice in one run — `assert(ConstraintCheck(),
        // ConstraintCheck())` — the second pass has nothing left to answer for.
        .filterNot { model.hasEvaluated(it) }
        .flatMap { declared ->
            model.markEvaluated(declared)
            violationsOf(model, declared)
        }

    override fun toString(): String = "ConstraintCheck"
}

/**
 * One constraint, evaluated against the files this run's walk found for it.
 *
 * Each constraint is caught on its own, for the reason the walk catches each file on its own:
 * one broken rule must not take the answers of every other rule with it.
 */
private fun violationsOf(model: ProjectModel, declared: DeclaredConstraint): List<Violation> {
    val files = model.filesUnder(declared)
    // Nothing to be about. Not a violation: a wildcard module key that has not filled up yet
    // is the same declaration the layout check already treats as normal, and a place with no
    // files hides no broken rule. It counts as seen, so `NotEvaluated` does not fire either.
    // TODO(v0.1 step 5): once Warning exists, decide whether this should be one.
    if (files.isEmpty()) return emptyList()

    val order = files.withIndex().associate { (index, file) -> file to index }
    val subject = ConstraintSubject(
        role = declared.role,
        name = declared.name,
        declaredAt = declared.declaredAt,
        paths = declared.paths,
        projectRoot = model.projectRootPath,
        files = files,
        shared = model.scratch(),
    )
    val failures = catching {
        val answered = declared.check.evaluate(subject)
        // A backend answering about files it was never handed has no way of noticing that on
        // its own. Dropping those answers silently would turn a backend that has stopped
        // seeing the right files into a rule that looks satisfied.
        val outside = answered.map { it.file }.filterNot { it in order }.distinct()
        if (outside.isNotEmpty()) {
            throw KatachiConstraintSubjectException(
                role = declared.role.qualifiedName,
                constraintName = declared.name,
                declaredAt = declared.declaredAt,
                outside = outside,
            )
        }
        answered
    }.getOrElse { cause ->
        return listOf(uncheckedConstraintOf(declared, UncheckedConstraintReason.Failed, cause))
    }

    val seen = LinkedHashSet<ConstraintFailure>()
    return failures
        // One declaration rejected twice by one block is one violation.
        .filter { seen.add(it) }
        .sortedBy { order.getValue(it.file) }
        .map { failure ->
            UnsatisfiedConstraint(
                path = failure.file,
                declaration = failure.declaration,
                line = failure.line,
                role = declared.role,
                constraintName = declared.name,
                layoutPath = declared.layoutPath,
                declaredAt = declared.declaredAt,
            )
        }
}

/** One [UncheckedConstraint], built from what the declaration already knows. */
internal fun uncheckedConstraintOf(
    declared: DeclaredConstraint,
    reason: UncheckedConstraintReason,
    cause: Throwable?,
): UncheckedConstraint = UncheckedConstraint(
    path = declared.reportPath,
    reason = reason,
    role = declared.role,
    constraintName = declared.name,
    layoutPath = declared.layoutPath,
    declaredAt = declared.declaredAt,
    cause = cause,
)

/**
 * The constraints nobody evaluated, as violations.
 *
 * This is what closes the worst way this library could break — a definition full of rules,
 * code breaking them, and a green test — when `ConstraintCheck()` was left out of the
 * arguments. A project that declares no constraints gets an empty list, so a user who has
 * never written one never sees any of this.
 */
internal fun ProjectModel.unevaluatedConstraintViolations(): List<Violation> =
    unevaluatedConstraints.map {
        uncheckedConstraintOf(it, UncheckedConstraintReason.NotEvaluated, cause = null)
    }
