// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * Same as `katachi uncheck <items>...` on the command line: marks items of the check list not done.
 *
 * ## Example 1: pass the arguments
 *
 * ```kt
 * val command = Katachi().uncheck("3-2")
 * println(command) // katachi uncheck 3-2
 * ```
 *
 * @param items The ids of the items, such as `3-2`.
 */
public fun Katachi.uncheck(vararg items: String): UncheckCommand =
    UncheckCommand(this, CommandLine(listOf("uncheck")).withPositionals(items.toList()))

/**
 * The `katachi uncheck` command: marks items of the check list not done.
 *
 * It takes no options, so building one is writing `katachi uncheck` in a shell.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().uncheck("3-2")
 * println(command) // katachi uncheck 3-2
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi uncheck 3-2
 * ```
 *
 * @see Katachi
 */
public class UncheckCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = UncheckCommand(katachi, line)

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi uncheck ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): UncheckCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi uncheck 3-2`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
