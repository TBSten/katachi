package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.assertNoErrors
import me.tbsten.katachi.check.internal.uncheckedFileConstraintOf
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.internal.ProjectWalk
import me.tbsten.katachi.processor.internal.projectWalk

/**
 * The check that evaluates `fileConstraint { }` blocks — the `konsist { }` ones included.
 *
 * It is not run unless it is asked for. `assert()` walks the tree and checks the layout;
 * `assert(FileConstraintCheck())` does that *and* runs the constraints. Making it explicit is
 * what lets a definition holding rules nobody evaluated be reported rather than pass quietly:
 * a constraint no check took responsibility for comes back as
 * [UncheckedFileConstraintReason.NotEvaluated].
 *
 * Passing it twice changes nothing — the second instance finds every constraint already
 * answered for and returns nothing, so no violation is counted twice.
 *
 * It answers through [assertNoErrors], like [LayoutCheck]: a constraint that could not be
 * evaluated is reported as an [UncheckedFileConstraint] in that answer rather than thrown, and
 * whatever else stops it is a `Result.failure` rather than a throw out of `process`.
 *
 * ## Example 1: the one line a project adds to evaluate its constraints
 * ```kt
 * @OptIn(ExperimentalKatachiApi::class)
 * class ProjectArchitectureTest {
 *     @Test
 *     fun `the project matches its declaration`() = projectArchitecture.assert(FileConstraintCheck())
 * }
 * ```
 *
 * ## Example 2: read what the constraints found without failing the test
 * ```kt
 * projectArchitecture.validate(FileConstraintCheck())
 *     .filterIsInstance<UnsatisfiedFileConstraint>()
 *     .map { it.path } shouldBe emptyList()
 * ```
 *
 * ## Example 3: run it from the command line
 * ```kts
 * // architecture-test/build.gradle.kts
 * katachi {
 *     processors {
 *         register("fileConstraint", "me.tbsten.katachi.check.FileConstraintCheck")
 *     }
 * }
 * ```
 * ```sh
 * ./gradlew :architecture-test:katachiFileConstraint
 * ```
 *
 * Like [LayoutCheck], it is not registered by default. The module still needs
 * `me.tbsten.katachi:katachi-konsist` on its test classpath for the `konsist { }` blocks to be
 * evaluated at all.
 */
@ExperimentalKatachiApi
public class FileConstraintCheck : ArchitectureProcessorNoArg<List<Violation>> {
    /** Evaluates every constraint of this run that nothing has evaluated yet. */
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<Violation>> =
        runProcessorCatching {
            val walk = context.projectWalk
            walk.declaredFileConstraints
                // Handed the same constraint twice in one run — `assert(FileConstraintCheck(),
                // FileConstraintCheck())` — the second pass has nothing left to answer for.
                .filterNot { walk.hasEvaluated(it) }
                .flatMap { declared ->
                    walk.markEvaluated(declared)
                    violationsOf(walk, declared)
                }
                .assertNoErrors(walk.projectRoot)
        }

    override fun toString(): String = "FileConstraintCheck"
}

/**
 * One constraint, evaluated against the files this run's walk found for it.
 *
 * Each constraint is caught on its own, for the reason the walk catches each file on its own:
 * one broken rule must not take the answers of every other rule with it.
 */
private fun violationsOf(walk: ProjectWalk, declared: DeclaredFileConstraint): List<Violation> {
    val files = walk.filesUnder(declared)
    // Nothing to be about. Not a violation and not a warning either: a place with no files yet
    // is what `layout { }` already treats as normal, and `UncheckedFileConstraintReason` dropped
    // `NoMatchingFiles` for that same reason. A warning here would fire on every healthy young
    // module and teach the reader to skip the section the real warnings live in. It counts as
    // seen, so `NotEvaluated` does not fire either.
    if (files.isEmpty()) return emptyList()

    val order = files.withIndex().associate { (index, file) -> file to index }
    val subject = FileConstraintSubject(
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
            throw KatachiFileConstraintSubjectException(
                role = declared.role.qualifiedName,
                constraintName = declared.name,
                declaredAt = declared.declaredAt,
                outside = outside,
                projectRoot = walk.projectRoot,
            )
        }
        answered
    }.getOrElse { cause ->
        return listOf(uncheckedFileConstraintOf(declared, UncheckedFileConstraintReason.Failed, cause))
    }

    val seen = LinkedHashSet<FileConstraintFailure>()
    return failures
        // One declaration rejected twice by one block is one violation.
        .filter { seen.add(it) }
        .sortedBy { order.getValue(it.file) }
        .map { failure ->
            UnsatisfiedFileConstraint(
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
