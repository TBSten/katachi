// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * The `katachi lint` command: lists what in the definition looks off: a Role with files of several kinds, flat groups and the like.
 *
 * Building one is writing `katachi lint ...` in a shell: the methods are named after the
 * options of `katachi lint --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().lint
 * println(command) // katachi lint
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi lint
 * ```
 *
 * @see Katachi
 */
public class LintCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = LintCommand(katachi, line)

    /**
     * Same as `katachi lint --module-dir <path>` on the command line: where the verification module is, relative to the Gradle root. Without it, `meta.moduleDir` of the check list, then `architecture-test`.
     *
     * @param path Such as `static-analysis/katachi`.
     */
    public fun `module-dir`(path: String): LintCommand = next(line.with("--module-dir", path))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi lint ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): LintCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi lint`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
