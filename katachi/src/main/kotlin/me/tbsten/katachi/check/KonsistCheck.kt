package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.catching
import me.tbsten.katachi.dsl.ConstraintFailure
import me.tbsten.katachi.dsl.ConstraintSubject
import me.tbsten.katachi.dsl.DeclaredConstraint
import me.tbsten.katachi.dsl.reportPath
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.ProjectWalk
import me.tbsten.katachi.processor.projectWalk
import me.tbsten.katachi.runProcessorCatching
import me.tbsten.katachi.scan.*

/**
 * The check that evaluates `constraint { }` blocks — the `konsist { }` ones included.
 *
 * It is not run unless it is asked for. `assert()` walks the tree and checks the layout;
 * `assert(KonsistCheck())` does that *and* runs the constraints. Making it explicit is
 * what lets a definition holding rules nobody evaluated be reported rather than pass quietly:
 * a constraint no check took responsibility for comes back as
 * [UncheckedConstraintReason.NotEvaluated].
 *
 * Passing it twice changes nothing — the second instance finds every constraint already
 * answered for and returns nothing, so no violation is counted twice.
 *
 * It answers through [assertNoErrors], like [LayoutCheck]: a constraint that could not be
 * evaluated is reported as an [UncheckedConstraint] in that answer rather than thrown, and
 * whatever else stops it is a `Result.failure` rather than a throw out of `process`.
 *
 * ## Example 1: the one line a project adds to evaluate its constraints
 * ```kt
 * @OptIn(ExperimentalKatachiApi::class)
 * class ProjectArchitectureTest {
 *     @Test
 *     fun `the project matches its declaration`() = projectArchitecture.assert(KonsistCheck())
 * }
 * ```
 *
 * ## Example 2: read what the constraints found without failing the test
 * ```kt
 * projectArchitecture.validate(KonsistCheck())
 *     .filterIsInstance<UnsatisfiedConstraint>()
 *     .map { it.path } shouldBe emptyList()
 * ```
 *
 * ## Example 3: run it from the command line
 * ```kts
 * // architecture-test/build.gradle.kts
 * katachi {
 *     processors {
 *         register("konsist", "me.tbsten.katachi.check.KonsistCheck")
 *     }
 * }
 * ```
 * ```sh
 * ./gradlew :architecture-test:runKatachiProcessor --processor=konsist
 * ```
 *
 * Like [LayoutCheck], it is not registered by default. The module still needs
 * `me.tbsten.katachi:katachi-konsist` on its test classpath for the `konsist { }` blocks to be
 * evaluated at all.
 */
@ExperimentalKatachiApi
public class KonsistCheck : ArchitectureProcessorNoArg<List<Violation>> {
    /** Evaluates every constraint of this run that nothing has evaluated yet. */
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<Violation>> =
        runProcessorCatching {
            val walk = context.projectWalk
            walk.declaredConstraints
                // Handed the same constraint twice in one run — `assert(KonsistCheck(),
                // KonsistCheck())` — the second pass has nothing left to answer for.
                .filterNot { walk.hasEvaluated(it) }
                .flatMap { declared ->
                    walk.markEvaluated(declared)
                    violationsOf(walk, declared)
                }
                .assertNoErrors()
        }

    override fun toString(): String = "KonsistCheck"
}

/**
 * One constraint, evaluated against the files this run's walk found for it.
 *
 * Each constraint is caught on its own, for the reason the walk catches each file on its own:
 * one broken rule must not take the answers of every other rule with it.
 */
private fun violationsOf(walk: ProjectWalk, declared: DeclaredConstraint): List<Violation> {
    val files = walk.filesUnder(declared)
    // Nothing to be about. Not a violation and not a warning either: a place with no files yet
    // is what `layout { }` already treats as normal, and `UncheckedConstraintReason` dropped
    // `NoMatchingFiles` for that same reason. A warning here would fire on every healthy young
    // module and teach the reader to skip the section the real warnings live in. It counts as
    // seen, so `NotEvaluated` does not fire either.
    if (files.isEmpty()) return emptyList()

    val order = files.withIndex().associate { (index, file) -> file to index }
    val subject = ConstraintSubject(
        role = declared.role,
        name = declared.name,
        declaredAt = declared.declaredAt,
        paths = declared.paths,
        projectRoot = walk.projectRootPath,
        files = files,
        shared = walk.scratch(),
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
private fun uncheckedConstraintOf(
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
 * code breaking them, and a green test — when `KonsistCheck()` was left out of the
 * arguments. A project that declares no constraints gets an empty list, so a user who has
 * never written one never sees any of this.
 */
internal fun ProjectWalk.unevaluatedConstraintViolations(): List<Violation> =
    unevaluatedConstraints.map {
        uncheckedConstraintOf(it, UncheckedConstraintReason.NotEvaluated, cause = null)
    }
