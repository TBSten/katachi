package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.RealFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.scan.UncheckedCheck
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.scan.catching

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
 * @throws me.tbsten.katachi.fs.KatachiProjectRootNotFoundException when no directory above the
 *   working directory carries a Gradle, Maven or git marker.
 * @throws me.tbsten.katachi.fs.KatachiGitUnavailableException when `files = gitTracked()` and
 *   the project root is a git repository, but git cannot be run.
 */
@InternalKatachiApi
public fun Architecture.validate(): List<Violation> = validate(RealFileSystem())

/**
 * [validate] against [fileSystem], which katachi's own specs use to hand it a tree that only
 * exists in memory.
 *
 * The walk is reached through [LayoutCheck] and the model it is handed, which is also why the
 * project root is resolved when this is called rather than when the model is built: the check
 * asks for the violations straight away, and asking is what starts the walk. See
 * `scanProject` for what that walk does and does not catch.
 *
 * As with `validate()`, the overload taking checks runs those too; this one runs [LayoutCheck]
 * alone.
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
 * [me.tbsten.katachi.scan.UncheckedCheck] violation naming the check and what it threw, and
 * every other check still reports what it found. The exception is the family the walk itself
 * refuses to swallow (`VirtualMachineError`, `LinkageError`, `InterruptedException`,
 * `AssertionError`), which passes straight through.
 *
 * The first check is a separate parameter rather than part of the vararg so that this cannot
 * be reached by `validate()`, `validate(10)` or `validate(fileSystem)`.
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
    check: ArchitectureProcessor<List<Violation>>,
    vararg more: ArchitectureProcessor<List<Violation>>,
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
    check: ArchitectureProcessor<List<Violation>>,
    vararg more: ArchitectureProcessor<List<Violation>>,
): List<Violation> = validateWith(fileSystem, listOf(check) + more)

/**
 * What all four `validate` overloads are: one walk, [LayoutCheck] plus [checks], one list.
 */
internal fun Architecture.validateWith(
    fileSystem: KatachiFileSystem,
    checks: List<ArchitectureProcessor<List<Violation>>>,
): List<Violation> {
    // `LayoutCheck` runs below whatever was passed. Letting a passed one through as well would
    // put the same `Violation` instance in the list twice: the count, the blocks and the
    // truncation budget would all double for a caller who only spelled out what already
    // happens.
    val extra = checks.filterNot { it is LayoutCheck }
    return process(fileSystem) { model ->
        // `LayoutCheck` does not appear in any signature above. Deny by default is the whole
        // of what katachi is, so it cannot depend on the caller remembering to ask for it.
        val layout = LayoutCheck().process(model)
        // One check at a time, each caught on its own: errors.md's "if this fails, can the
        // neighbour still answer" applies here exactly as it applies per file inside the walk.
        // A third-party check throwing once must not take the layout violations with it —
        // that is the failure this library can least afford.
        val found = extra.flatMap { check ->
            catching { check.process(model) }.getOrElse { cause ->
                listOf(
                    UncheckedCheck(
                        check = check::class.qualifiedName ?: check::class.java.name,
                        cause = cause,
                    ),
                )
            }
        }
        // One model, therefore one walk.
        // TODO(v0.1 step 5): add `+ model.unevaluatedConstraintViolations()` here, so that a
        //  constraint nothing evaluated is reported rather than silently passing.
        (layout + found).sortedBy { it.kind.ordinal }
    }
}
