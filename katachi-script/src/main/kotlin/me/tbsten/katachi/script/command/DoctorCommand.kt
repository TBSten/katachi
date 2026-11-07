// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run
import me.tbsten.katachi.script.option.Lang

/**
 * The `katachi doctor` command: checks the JDK Gradle uses, the project's Kotlin version and whether git is there.
 *
 * Building one is writing `katachi doctor ...` in a shell: the methods are named after the
 * options of `katachi doctor --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().doctor.lang(ja)
 * println(command) // katachi doctor --lang ja
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi doctor --lang ja
 * ```
 *
 * @see Katachi
 */
public class DoctorCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = DoctorCommand(katachi, line)

    /**
     * Same as `katachi doctor --lang <en|ja>` on the command line: the language of the command's messages.
     *
     * @param lang [en] or [ja].
     */
    public fun lang(lang: Lang): DoctorCommand = next(line.with("--lang", lang.value))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi doctor ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): DoctorCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi doctor --lang ja`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
