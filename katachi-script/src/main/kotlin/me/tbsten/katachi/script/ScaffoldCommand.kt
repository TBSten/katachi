package me.tbsten.katachi.script

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.requireOption
import me.tbsten.katachi.script.internal.run

/**
 * `katachi scaffold`: creates the test module and adds it to the build.
 *
 * The methods are named after the options of `katachi scaffold --help`. Each returns a new
 * command; calling one twice keeps the last value. `--package` is required and is checked by
 * [execute], not while the command is built.
 *
 * ## Example 1: scaffold a Kotest module and print the line first
 *
 * ```kt
 * val scaffold = Katachi().scaffold
 *     .`package`("me.tbsten.katachi.sample")
 *     .`test-framework`(kotest)
 * println(scaffold) // katachi scaffold --package me.tbsten.katachi.sample --test-framework kotest
 * scaffold.execute()
 * ```
 *
 * @see Katachi.scaffold
 */
public class ScaffoldCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
    private val declaredAt: DeclarationSite,
) : KatachiCommand {
    private fun next(line: CommandLine) = ScaffoldCommand(katachi, line, declaredAt)

    /**
     * `--package`: the package of the app. Required.
     *
     * @param name Such as `com.example.app`.
     */
    public fun `package`(name: String): ScaffoldCommand = next(line.with("--package", name))

    /**
     * `--katachi`: the katachi version to depend on. Without it, the version `init` recorded.
     *
     * @param version Such as `0.4.0`.
     */
    public fun katachi(version: String): ScaffoldCommand = next(line.with("--katachi", version))

    /**
     * `--kotlin`: the Kotlin version. Without it, the one found in the project.
     *
     * @param version Such as `2.2.21`.
     */
    public fun kotlin(version: String): ScaffoldCommand = next(line.with("--kotlin", version))

    /**
     * `--no-konsist`: lays the module out without katachi-konsist, so `ktFileContent { }` is not available.
     *
     * @param enabled False takes the option off again.
     */
    public fun `no-konsist`(enabled: Boolean = true): ScaffoldCommand = next(line.withFlag("--no-konsist", enabled))

    /**
     * `--module-dir`: where the test module goes, relative to the Gradle root.
     *
     * @param path Such as `static-analysis/katachi`.
     */
    public fun `module-dir`(path: String): ScaffoldCommand = next(line.with("--module-dir", path))

    /**
     * `--test-framework`: the framework the generated tests use.
     *
     * @param framework [kotest] or [junit5].
     */
    public fun `test-framework`(framework: TestFramework): ScaffoldCommand =
        next(line.with("--test-framework", framework.value))

    /**
     * `--deps`: how the dependencies are written.
     *
     * @param style [catalog] or [literal].
     */
    public fun deps(style: DependencyStyle): ScaffoldCommand = next(line.with("--deps", style.value))

    /**
     * `--force`: overwrites an existing test module.
     *
     * @param enabled False takes the option off again.
     */
    public fun force(enabled: Boolean = true): ScaffoldCommand = next(line.withFlag("--force", enabled))

    /**
     * `--dry-run`: prints what would be written, and writes nothing.
     *
     * @param enabled False takes the option off again.
     */
    public fun `dry-run`(enabled: Boolean = true): ScaffoldCommand = next(line.withFlag("--dry-run", enabled))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): ScaffoldCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int {
        requireOption(line, "--package", declaredAt)
        return katachi.run(arguments)
    }

    /** The command line as typed in a shell: `katachi scaffold --package com.example.app`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
