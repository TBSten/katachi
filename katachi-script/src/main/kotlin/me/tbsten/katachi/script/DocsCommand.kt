// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * Same as `katachi docs` on the command line: fetches the katachi guide into the working directory and prints the path.
 */
public val Katachi.docs: DocsCommand
    get() = DocsCommand(this, CommandLine(listOf("docs")))

/**
 * The `katachi docs` command: fetches the katachi guide into the working directory and prints the path.
 *
 * Building one is writing `katachi docs ...` in a shell: the methods are named after the
 * options of `katachi docs --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().docs.topic("layout")
 * println(command) // katachi docs --topic layout
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi docs --topic layout
 * ```
 *
 * @see Katachi
 */
public class DocsCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = DocsCommand(katachi, line)

    /**
     * Same as `katachi docs --small` on the command line: fetches the shorter guide (`llms-small.txt`) instead of the whole one.
     *
     * @param enabled False takes the option off again.
     */
    public fun small(enabled: Boolean = true): DocsCommand = next(line.withFlag("--small", enabled))

    /**
     * Same as `katachi docs --topics` on the command line: lists the topics `--topic` takes.
     *
     * @param enabled False takes the option off again.
     */
    public fun topics(enabled: Boolean = true): DocsCommand = next(line.withFlag("--topics", enabled))

    /**
     * Same as `katachi docs --topic <name>` on the command line: fetches one page of the guide.
     *
     * @param name Such as `layout` or `kt-file-content`.
     */
    public fun topic(name: String): DocsCommand = next(line.with("--topic", name))

    /**
     * Same as `katachi docs --api` on the command line, without a value: fetches the API reference: the index without a module, the whole reference of one module with it.
     */
    public fun api(): DocsCommand = next(line.with("--api", null))

    /**
     * Same as `katachi docs --api [<katachi|katachi-konsist>]` on the command line: fetches the API reference: the index without a module, the whole reference of one module with it.
     *
     * @param module [ApiModule.Katachi] or [ApiModule.KatachiKonsist].
     */
    public fun api(module: ApiModule): DocsCommand = next(line.with("--api", module.value))

    /**
     * Same as `katachi docs --refresh` on the command line: fetches again even when the working directory has a copy.
     *
     * @param enabled False takes the option off again.
     */
    public fun refresh(enabled: Boolean = true): DocsCommand = next(line.withFlag("--refresh", enabled))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi docs ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): DocsCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi docs --topic layout`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
