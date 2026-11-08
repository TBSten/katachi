// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run
import me.tbsten.katachi.script.option.ConstraintNames
import me.tbsten.katachi.script.option.DependencyStyle
import me.tbsten.katachi.script.option.TestFramework

/**
 * The subcommands of `katachi agent preferences`: shows and records how the definition is written.
 *
 * Each one returns the builder of that subcommand. `katachi agent preferences` alone is the same as [show].
 *
 * ## Example 1: pick a subcommand
 *
 * ```kt
 * val command = Katachi().agent.preferences.show
 * println(command) // katachi agent preferences show
 * command.execute()
 * ```
 *
 * @see Katachi
 */
public class PreferencesCommandGroup internal constructor(private val katachi: Katachi) {
    /**
     * Same as `katachi agent preferences show` on the command line: prints each preference, its value and where it came from.
     */
    public val show: PreferencesShowCommand
        get() = PreferencesShowCommand(katachi, CommandLine(listOf("agent", "preferences", "show")))

    /**
     * Same as `katachi agent preferences set` on the command line: records how the definition is written.
     */
    public val set: PreferencesSetCommand
        get() = PreferencesSetCommand(katachi, CommandLine(listOf("agent", "preferences", "set")))
}

/**
 * The `katachi agent preferences show` command: prints each preference, its value and where it came from.
 *
 * It takes no options, so building one is writing `katachi agent preferences show` in a shell.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.preferences.show
 * println(command) // katachi agent preferences show
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent preferences show
 * ```
 *
 * @see Katachi
 */
public class PreferencesShowCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = PreferencesShowCommand(katachi, line)

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent preferences show ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): PreferencesShowCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent preferences show`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent preferences set` command: records how the definition is written.
 *
 * Building one is writing `katachi agent preferences set ...` in a shell: the methods are named after the
 * options of `katachi agent preferences set --help` and add the same `key=value`. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.preferences.set.nestedGroups(true).testFramework(kotest)
 * println(command) // katachi agent preferences set nestedGroups=yes testFramework=kotest
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent preferences set nestedGroups=yes testFramework=kotest
 * ```
 *
 * @see Katachi
 */
public class PreferencesSetCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = PreferencesSetCommand(katachi, line)

    /**
     * Same as `katachi agent preferences set nestedGroups=<yes|no>` on the command line: whether groups are nested.
     *
     * @param enabled True for `yes`, false for `no`.
     */
    public fun nestedGroups(enabled: Boolean): PreferencesSetCommand =
        next(line.withKeyValue("nestedGroups", if (enabled) "yes" else "no"))

    /**
     * Same as `katachi agent preferences set moduleDir=<path>` on the command line: where the test module goes, recorded as `meta.moduleDir` of the check list.
     *
     * @param path Such as `static-analysis/katachi`.
     */
    public fun moduleDir(path: String): PreferencesSetCommand = next(line.withKeyValue("moduleDir", path))

    /**
     * Same as `katachi agent preferences set testFramework=<junit5|kotest|kotest-fun|kotest-string|kotest-should|kotest-describe|kotest-behavior|kotest-word|kotest-free|kotest-feature|kotest-expect|kotest-annotation>` on the command line: the test framework `scaffold` picks.
     *
     * @param framework [junit5], [kotest], [kotestFun], [kotestString], [kotestShould], [kotestDescribe], [kotestBehavior], [kotestWord], [kotestFree], [kotestFeature], [kotestExpect] or [kotestAnnotation].
     */
    public fun testFramework(framework: TestFramework): PreferencesSetCommand =
        next(line.withKeyValue("testFramework", framework.value))

    /**
     * Same as `katachi agent preferences set dependencyStyle=<catalog|literal>` on the command line: how `scaffold` writes the dependencies.
     *
     * @param style [catalog] or [literal].
     */
    public fun dependencyStyle(style: DependencyStyle): PreferencesSetCommand =
        next(line.withKeyValue("dependencyStyle", style.value))

    /**
     * Same as `katachi agent preferences set constraintNames=<plain|numbered>` on the command line: whether constraint names carry the question number.
     *
     * @param names [plain] or [numbered].
     */
    public fun constraintNames(names: ConstraintNames): PreferencesSetCommand =
        next(line.withKeyValue("constraintNames", names.value))

    /**
     * Same as `katachi agent preferences set oneRolePerFile=<yes|no>` on the command line: whether each Role is written in a file of its own.
     *
     * @param enabled True for `yes`, false for `no`.
     */
    public fun oneRolePerFile(enabled: Boolean): PreferencesSetCommand =
        next(line.withKeyValue("oneRolePerFile", if (enabled) "yes" else "no"))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent preferences set ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): PreferencesSetCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent preferences set nestedGroups=yes testFramework=kotest`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
