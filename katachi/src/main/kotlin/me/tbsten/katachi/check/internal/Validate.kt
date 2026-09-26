package me.tbsten.katachi.check.internal

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.LayoutCheck
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.processor.internal.projectWalk

/**
 * Runs the check against the real file system and returns every violation.
 *
 * This is [LayoutCheck] run through `process`, kept as a name of its own because it is the
 * shape the check has always had here and nothing about it needs to change: a caller who wants
 * the violations of the layout says so, without having to know that the check became one
 * processor among others underneath.
 *
 * Nothing is thrown, including when the definition allows nothing at all: `assert()` is what
 * turns this into a test failure, and a report or a baseline can sit on it instead.
 *
 * The overload taking checks runs those too, on the same walk; this one runs [LayoutCheck]
 * alone.
 *
 * ## Example 1: read how many violations there are without failing anything
 * ```kt
 * projectArchitecture.validate().count { it.severity == Severity.Error }
 * ```
 *
 * @throws me.tbsten.katachi.dsl.KatachiProjectRootNotFoundException when no directory above the
 *   working directory carries a Gradle, Maven or git marker.
 * @throws me.tbsten.katachi.dsl.KatachiGitUnavailableException when `files = gitTracked()` and
 *   the project root is a git repository, but git cannot be run.
 */
@InternalKatachiApi
public fun Architecture.validate(): List<Violation> = validate(RealFileSystem())

/**
 * [validate] against [fileSystem], which katachi's own specs use to hand it a tree that only
 * exists in memory.
 *
 * The walk is reached through [LayoutCheck] and the context it is handed, which is also why the
 * project root is resolved when this is called rather than when the context is built: the check
 * asks for the violations straight away, and asking is what starts the walk. See
 * `scanProject` for what that walk does and does not catch.
 *
 * As with `validate()`, the overload taking checks runs those too; this one runs [LayoutCheck]
 * alone.
 *
 * ## Example 1: check a definition against a tree that only exists in memory
 * ```kt
 * val violations = definition.validate(fakeFileSystem)
 * ```
 */
@InternalKatachiApi
public fun Architecture.validate(fileSystem: KatachiFileSystem): List<Violation> =
    validateWith(fileSystem, emptyList())

/**
 * [validate] with more checks than the layout one, all on the same walk of the project.
 *
 * [LayoutCheck] runs whether or not it is in the arguments — deny by default is not something
 * a caller has to remember to switch on — and passing it anyway changes nothing: it is
 * dropped from [more] rather than run twice, so no violation is counted, blocked or truncated
 * twice.
 *
 * A check that throws does not end the run. It becomes one
 * [me.tbsten.katachi.check.UncheckedCheck] violation naming the check and what it threw, and
 * every other check still reports what it found. A check that answers `Result.failure` is read
 * by what the failure is: a [KatachiArchitectureAssertionError] -- what [me.tbsten.katachi.check.assertNoErrors]
 * throws -- is the check saying what it found, and its violations join the list; any other
 * failure is the check saying it could not tell, and becomes an `UncheckedCheck` like a throw.
 * A check is expected to answer rather than throw, but one that throws anyway is caught here
 * all the same.
 *
 * [LayoutCheck] is read differently. It keeps what stops it as a `failure` rather than
 * throwing, and here that failure is thrown on: no project root, or a definition that cannot
 * be read, is this call's own exception, not one check among others that could not tell.
 *
 * The exception to "a throw does not end the run" is the family the walk itself refuses to
 * swallow (`VirtualMachineError`, `LinkageError`, `InterruptedException`, `AssertionError`),
 * which passes straight through.
 *
 * The first check is a separate parameter rather than part of the vararg so that this cannot
 * be reached by `validate()` or `validate(fileSystem)`.
 *
 * ## Example 1: read what a check of your own found, without failing the test
 * ```kt
 * val violations = projectArchitecture.validate(TodoCheck())
 * violations.map { "[${it.label}] ${it.path}" } shouldContain "[TodoRule] app/src/Foo.kt"
 * ```
 *
 * ## Example 2: see which checks could not be run at all
 * ```kt
 * projectArchitecture.validate(TodoCheck(), NamingCheck())
 *     .filterIsInstance<UncheckedCheck>()
 *     .map { it.check } shouldBe emptyList()
 * ```
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun Architecture.validate(
    check: ArchitectureProcessor<Unit, List<Violation>>,
    vararg more: ArchitectureProcessor<Unit, List<Violation>>,
): List<Violation> = validateWith(RealFileSystem(), listOf(check) + more)

/**
 * [validate] with checks, against [fileSystem]: the in-memory tree katachi's own specs use,
 * plus whatever checks the spec is about.
 *
 * ## Example 1: run a check of your own against a tree that only exists in memory
 * ```kt
 * val violations = definition.validate(fakeFileSystem, TodoCheck())
 * violations.map { it.label } shouldContain "TodoRule"
 * ```
 */
@InternalKatachiApi
@ExperimentalKatachiApi
public fun Architecture.validate(
    fileSystem: KatachiFileSystem,
    check: ArchitectureProcessor<Unit, List<Violation>>,
    vararg more: ArchitectureProcessor<Unit, List<Violation>>,
): List<Violation> = validateWith(fileSystem, listOf(check) + more)

/**
 * What all four `validate` overloads are: one walk, [LayoutCheck] plus [checks], one list.
 */
internal fun Architecture.validateWith(
    fileSystem: KatachiFileSystem,
    checks: List<ArchitectureProcessor<Unit, List<Violation>>>,
): List<Violation> = validateWithRoot(fileSystem, checks).violations

/** [validateWith]'s violations, and the project root the walk resolved -- what a report needs. */
internal data class ValidationResult(val violations: List<Violation>, val projectRoot: FsPath)

/** [validateWith], also handing back the project root so `assert` can print absolute paths. */
internal fun Architecture.validateWithRoot(
    fileSystem: KatachiFileSystem,
    checks: List<ArchitectureProcessor<Unit, List<Violation>>>,
): ValidationResult {
    // `LayoutCheck` runs below whatever was passed. Letting a passed one through as well would
    // put the same `Violation` instance in the list twice: the count, the blocks and the
    // truncation budget would all double for a caller who only spelled out what already
    // happens.
    val extra = checks.filterNot { it is LayoutCheck }
    return process(fileSystem) { context ->
        // Started here rather than inside `LayoutCheck`, which keeps whatever it threw as its
        // answer: an exception the walk hands up -- the caller's own assertion from inside the
        // file system included -- would otherwise come back looking like what the check found.
        context.projectWalk.layoutViolations
        // `LayoutCheck` does not appear in any signature above. Deny by default is the whole
        // of what katachi is, so it cannot depend on the caller remembering to ask for it.
        // A failure other than what it found is thrown on: a layout check that cannot run --
        // no project root, no git, a definition it cannot read -- leaves nothing to report, and
        // `validate()` documents those exceptions as its own.
        val layout = LayoutCheck().process(context).getOrElse { cause ->
            (cause as? KatachiArchitectureAssertionError)?.violations ?: throw cause
        }
        // One check at a time, each caught on its own: errors.md's "if this fails, can the
        // neighbour still answer" applies here exactly as it applies per file inside the walk.
        // A third-party check throwing once must not take the layout violations with it —
        // that is the failure this library can least afford.
        val found = extra.flatMap { check ->
            catching { check.process(context) }
                .getOrElse { cause -> return@flatMap listOf(uncheckedCheckOf(check, cause)) }
                .violationsOf(check)
        }
        // One context, therefore one walk. The last term is the guard against the quietest way
        // this library could break: a definition full of constraints, code breaking them, and
        // a green test because nothing was handed a check that evaluates them.
        val violations = (layout + found + context.projectWalk.unevaluatedFileConstraintViolations())
            .sortedBy { it.kind.ordinal }
        ValidationResult(violations, context.projectWalk.projectRoot)
    }
}
