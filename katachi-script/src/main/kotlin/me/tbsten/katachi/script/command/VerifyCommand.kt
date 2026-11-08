// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * The `katachi agent verify` command: lists what is left to fill in, and closes the last step when nothing is.
 *
 * It takes no options, so building one is writing `katachi agent verify` in a shell.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.verify
 * println(command) // katachi agent verify
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent verify
 * ```
 *
 * @see Katachi
 */
public class VerifyCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = VerifyCommand(katachi, line)

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent verify ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): VerifyCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent verify`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
