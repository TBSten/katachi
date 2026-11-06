// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * Same as `katachi warn <step> [<text>]` on the command line: puts a warning on a step of the check list, which keeps it open even when it is done.
 *
 * ## Example 1: pass the arguments
 *
 * ```kt
 * val command = Katachi().warn("4", "gradlew is missing")
 * println(command) // katachi warn 4 'gradlew is missing'
 * ```
 *
 * @param step The id of the step, such as `4`.
 * @param text The warning. Leave it out with `.clear()`.
 */
public fun Katachi.warn(step: String, text: String? = null): WarnCommand =
    WarnCommand(this, CommandLine(listOf("warn")).withPositionals(listOfNotNull(step, text)))

/**
 * The `katachi warn` command: puts a warning on a step of the check list, which keeps it open even when it is done.
 *
 * Building one is writing `katachi warn ...` in a shell: the methods are named after the
 * options of `katachi warn --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().warn("4", "gradlew is missing")
 * println(command) // katachi warn 4 'gradlew is missing'
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi warn 4 'gradlew is missing'
 * ```
 *
 * @see Katachi
 */
public class WarnCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = WarnCommand(katachi, line)

    /**
     * Same as `katachi warn --clear` on the command line: removes the warning instead of adding one.
     *
     * @param enabled False takes the option off again.
     */
    public fun clear(enabled: Boolean = true): WarnCommand = next(line.withFlag("--clear", enabled))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi warn ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): WarnCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi warn 4 'gradlew is missing'`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
