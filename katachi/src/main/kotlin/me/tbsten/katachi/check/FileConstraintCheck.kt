package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.assertNoErrors
import me.tbsten.katachi.check.internal.uncheckedFileConstraintOf
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.internal.ProjectWalk
import me.tbsten.katachi.processor.internal.projectWalk
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
 * It answers through [me.tbsten.katachi.check.assertNoErrors], like [LayoutCheck]: a constraint
 * that could not be evaluated is reported as an [UncheckedFileConstraint] in that answer rather
 * than thrown, and whatever else stops it is a `Result.failure` rather than a throw out of
 * `process`.
 *
 * ## Evaluating in parallel
 *
 * By default the constraints run one after another on the calling thread. With
 * `parallelism = n` up to `n` of them run at once, each on a thread of its own, and the answer
 * is still put together in declaration order, so the report reads exactly as it would have.
 * It is off by default because the blocks are the caller's code: a block that writes to state
 * it shares with another block is only safe while they take turns. [FileConstraintSubject.memo]
 * is safe either way. Registered by name from Gradle, the check always runs one at a time.
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
 * ## Example 2: read which files the constraints rejected
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.path }
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
 * ## Example 4: evaluate the constraints on several threads
 * ```kt
 * projectArchitecture.assert(
 *     FileConstraintCheck(parallelism = Runtime.getRuntime().availableProcessors()),
 * )
 * ```
 *
 * Like [LayoutCheck], it is not registered by default. The module still needs
 * `me.tbsten.katachi:katachi-konsist` on its test classpath for the `konsist { }` blocks to be
 * evaluated at all.
 *
 * @property parallelism how many constraints may be evaluated at once; `1`, the default,
 *   evaluates them one at a time on the calling thread.
 * @throws KatachiInvalidFileConstraintParallelismException when
 *   [parallelism][FileConstraintCheck.parallelism] is less than 1.
 * @constructor Takes how many constraints may run at once, one unless given.
 */
@ExperimentalKatachiApi
public class FileConstraintCheck(
    /** How many constraints may be evaluated at once; `1`, the default, takes them in turn. */
    public val parallelism: Int = 1,
) : ArchitectureProcessorNoArg<List<Violation>> {
    init {
        if (parallelism < 1) {
            throw KatachiInvalidFileConstraintParallelismException(parallelism, captureDeclarationSite())
        }
    }

    /** Evaluates every constraint of this run that nothing has evaluated yet. */
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<Violation>> =
        runProcessorCatching {
            val walk = context.projectWalk
            // Handed the same constraint twice in one run — `assert(FileConstraintCheck(),
            // FileConstraintCheck())` — the second pass has nothing left to answer for.
            val pending = walk.declaredFileConstraints.filterNot { walk.hasEvaluated(it) }
            val violations = if (parallelism == 1 || pending.size < 2) {
                pending.flatMap { declared ->
                    walk.markEvaluated(declared)
                    violationsOf(walk, declared, walk.filesUnder(declared))
                }
            } else {
                evaluateInParallel(walk, pending, parallelism)
            }
            violations.assertNoErrors(walk.projectRoot)
        }

    /** `FileConstraintCheck`, with `(parallelism=<n>)` when it runs more than one at once. */
    override fun toString(): String =
        if (parallelism == 1) "FileConstraintCheck" else "FileConstraintCheck(parallelism=$parallelism)"
}

/**
 * [pending] evaluated on up to [parallelism] threads, answered in declaration order.
 *
 * Only the constraints themselves leave the calling thread. The walk's bookkeeping -- which
 * files a block covers, which constraints were evaluated -- is kept on it, in the order the
 * one-at-a-time path keeps it: a constraint is marked evaluated as its answer is collected, so a
 * throw that ends the run leaves the ones after it unevaluated there too.
 */
private fun evaluateInParallel(
    walk: ProjectWalk,
    pending: List<DeclaredFileConstraint>,
    parallelism: Int,
): List<Violation> {
    val files = pending.map { walk.filesUnder(it) }
    // A backend may load classes through the context class loader (Gradle's test workers set
    // one), and a pool thread would otherwise carry the system loader.
    val loader = Thread.currentThread().contextClassLoader
    val created = AtomicInteger()
    val pool = Executors.newFixedThreadPool(minOf(parallelism, pending.size)) { task ->
        Thread(task, "katachi-file-constraint-${created.incrementAndGet()}").apply {
            isDaemon = true
            contextClassLoader = loader
        }
    }
    try {
        val answers = pending.mapIndexed { index, declared ->
            pool.submit(Callable { violationsOf(walk, declared, files[index]) })
        }
        return pending.zip(answers).flatMap { (declared, answer) ->
            walk.markEvaluated(declared)
            // `violationsOf` already keeps whatever a constraint throws as its answer, so what
            // arrives here is what the one-at-a-time path would have thrown: rethrown as is.
            try {
                answer.get()
            } catch (wrapped: ExecutionException) {
                throw wrapped.cause ?: wrapped
            }
        }
    } finally {
        pool.shutdownNow()
    }
}

/**
 * One constraint, evaluated against the files this run's walk found for it.
 *
 * Each constraint is caught on its own, for the reason the walk catches each file on its own:
 * one broken rule must not take the answers of every other rule with it.
 */
private fun violationsOf(
    walk: ProjectWalk,
    declared: DeclaredFileConstraint,
    files: List<String>,
): List<Violation> {
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
