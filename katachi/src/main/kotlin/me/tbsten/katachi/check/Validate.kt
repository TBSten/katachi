package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.validateWith
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessor

/**
 * Runs the check against the real file system and returns every violation.
 *
 * Runs [LayoutCheck] alone; the overload taking checks runs those too, on the same walk. No
 * violation is thrown: `assert()` is what turns them into a test failure.
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
@ExperimentalKatachiApi
public fun Architecture.validate(): List<Violation> = validate(RealFileSystem())

/**
 * [validate] against [fileSystem], for example a tree that only exists in memory.
 *
 * The project root is resolved when this is called. It runs [LayoutCheck] alone.
 *
 * ## Example 1: check a definition against a tree that only exists in memory
 * ```kt
 * val violations = definition.validate(fakeFileSystem)
 * ```
 */
@ExperimentalKatachiApi
public fun Architecture.validate(fileSystem: KatachiFileSystem): List<Violation> =
    validateWith(fileSystem, emptyList())

/**
 * [validate] with more checks than the layout one, all on the same walk of the project.
 *
 * [LayoutCheck] runs whether or not it is in the arguments, and passing it anyway does not run
 * it twice.
 *
 * A check that throws, or answers with a `Result.failure` other than a
 * [KatachiArchitectureAssertionError], becomes one [me.tbsten.katachi.check.UncheckedCheck] and
 * every other check still reports what it found. `VirtualMachineError`, `LinkageError`,
 * `InterruptedException` and `AssertionError` pass straight through. A layout check that cannot
 * run at all (no project root, no git) throws, as for `validate()`.
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
@ExperimentalKatachiApi
public fun Architecture.validate(
    check: ArchitectureProcessor<Unit, List<Violation>>,
    vararg more: ArchitectureProcessor<Unit, List<Violation>>,
): List<Violation> = validateWith(RealFileSystem(), listOf(check) + more)

/**
 * [validate] with checks, against [fileSystem], for example a tree that only exists in memory.
 *
 * ## Example 1: run a check of your own against a tree that only exists in memory
 * ```kt
 * val violations = definition.validate(fakeFileSystem, TodoCheck())
 * violations.map { it.label } shouldContain "TodoRule"
 * ```
 */
@ExperimentalKatachiApi
public fun Architecture.validate(
    fileSystem: KatachiFileSystem,
    check: ArchitectureProcessor<Unit, List<Violation>>,
    vararg more: ArchitectureProcessor<Unit, List<Violation>>,
): List<Violation> = validateWith(fileSystem, listOf(check) + more)
