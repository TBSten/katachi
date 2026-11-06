// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * Same as `katachi test` on the command line: runs the tests of the test module and prints only a summary.
 */
public val Katachi.test: TestCommand
    get() = TestCommand(this, CommandLine(listOf("test")))

/**
 * The `katachi test` command: runs the tests of the test module and prints only a summary.
 *
 * Building one is writing `katachi test ...` in a shell: the methods are named after the
 * options of `katachi test --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().test.save("before-refactor")
 * println(command) // katachi test --save before-refactor
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi test --save before-refactor
 * ```
 *
 * @see Katachi
 */
public class TestCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = TestCommand(katachi, line)

    /**
     * Same as `katachi test --save <name>` on the command line: also copies the log to `tmp/test-<name>.log` of the working directory.
     *
     * @param name Such as `before-refactor`.
     */
    public fun save(name: String): TestCommand = next(line.with("--save", name))

    /**
     * Same as `katachi test -- <arguments>...` on the command line: the arguments after `--`, handed to `./gradlew` as they are. Calling it again adds more.
     *
     * @param arguments Such as `--info`.
     */
    public fun gradle(vararg arguments: String): TestCommand = next(line.plusPassthrough(arguments.toList()))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi test ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): TestCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi test --save before-refactor`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
