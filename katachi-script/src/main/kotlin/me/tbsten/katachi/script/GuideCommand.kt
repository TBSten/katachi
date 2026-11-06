// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * Same as `katachi guide` on the command line, starting from this [Katachi]'s defaults: prints the install guide one section at a time, or its table of contents without a section.
 */
public val Katachi.guide: GuideCommand
    get() = GuideCommand(this, CommandLine(listOf("guide")).withNonEmpty("--lang", this.lang?.value))

/**
 * Same as `katachi guide <section>` on the command line, starting from this [Katachi]'s defaults: prints the install guide one section at a time, or its table of contents without a section.
 *
 * ## Example 1: pass the arguments
 *
 * ```kt
 * val command = Katachi().guide("3-2")
 * println(command) // katachi guide 3-2
 * ```
 *
 * @param section The number of a heading, such as `3-2`.
 */
public fun Katachi.guide(section: String): GuideCommand =
    GuideCommand(this, CommandLine(listOf("guide")).withNonEmpty("--lang", this.lang?.value).withPositionals(listOf(section)))

/**
 * The `katachi guide` command: prints the install guide one section at a time, or its table of contents without a section.
 *
 * Building one is writing `katachi guide ...` in a shell: the methods are named after the
 * options of `katachi guide --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().guide("3-2").lang(ja)
 * println(command) // katachi guide 3-2 --lang ja
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi guide 3-2 --lang ja
 * ```
 *
 * @see Katachi
 */
public class GuideCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = GuideCommand(katachi, line)

    /**
     * Same as `katachi guide --refresh` on the command line: writes the working directory's copy of the guide again.
     *
     * @param enabled False takes the option off again.
     */
    public fun refresh(enabled: Boolean = true): GuideCommand = next(line.withFlag("--refresh", enabled))

    /**
     * Same as `katachi guide --lang <en|ja>` on the command line: the language of the command's messages.
     *
     * @param lang [en] or [ja].
     */
    public fun lang(lang: Lang): GuideCommand = next(line.with("--lang", lang.value))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi guide ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): GuideCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi guide 3-2 --lang ja`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
