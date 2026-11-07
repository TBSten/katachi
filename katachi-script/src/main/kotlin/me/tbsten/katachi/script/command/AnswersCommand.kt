// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.KatachiCommand
import me.tbsten.katachi.script.Target
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * The subcommands of `katachi answers`: shows and records the answers to the questions.
 *
 * Each one returns the builder of that subcommand. `katachi answers` alone is the same as [show].
 *
 * ## Example 1: pick a subcommand
 *
 * ```kt
 * val command = Katachi().answers.show.target(report)
 * println(command) // katachi answers show --target report
 * command.execute()
 * ```
 *
 * @see Katachi
 */
public class AnswersCommandGroup internal constructor(private val katachi: Katachi) {
    /**
     * Same as `katachi answers show` on the command line: prints the answers to the questions. Without `--target`, those of both files.
     */
    public val show: AnswersShowCommand
        get() = AnswersShowCommand(katachi, CommandLine(listOf("answers", "show")))

    /**
     * Same as `katachi answers import <json>` on the command line: reads the answers the browser exported.
     *
     * @param json The path of the JSON the browser exported.
     */
    public fun import(json: String): AnswersImportCommand =
        AnswersImportCommand(katachi, CommandLine(listOf("answers", "import")).withPositionals(listOf(json)))

    /**
     * Same as `katachi answers set <id> <answer>` on the command line: writes the answer to one question.
     *
     * @param id The id of the question: `q<n>` of the check list or `cq<n>` of the report.
     * @param answer The answer.
     */
    public fun set(id: String, answer: String): AnswersSetCommand =
        AnswersSetCommand(katachi, CommandLine(listOf("answers", "set")).withPositionals(listOf(id, answer)))
}

/**
 * The `katachi answers show` command: prints the answers to the questions. Without `--target`, those of both files.
 *
 * Building one is writing `katachi answers show ...` in a shell: the methods are named after the
 * options of `katachi answers show --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().answers.show.target(report)
 * println(command) // katachi answers show --target report
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi answers show --target report
 * ```
 *
 * @see Katachi
 */
public class AnswersShowCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AnswersShowCommand(katachi, line)

    /**
     * Same as `katachi answers show --target <report|check-list>` on the command line: the file to read or write.
     *
     * @param target [report] or [checkList].
     */
    public fun target(target: Target): AnswersShowCommand = next(line.with("--target", target.value))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi answers show ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AnswersShowCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi answers show --target report`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi answers import` command: reads the answers the browser exported.
 *
 * Building one is writing `katachi answers import ...` in a shell: the methods are named after the
 * options of `katachi answers import --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().answers.import("answers.json").target(report)
 * println(command) // katachi answers import answers.json --target report
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi answers import answers.json --target report
 * ```
 *
 * @see Katachi
 */
public class AnswersImportCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AnswersImportCommand(katachi, line)

    /**
     * Same as `katachi answers import --target <report|check-list>` on the command line: the file to read or write.
     *
     * @param target [report] or [checkList].
     */
    public fun target(target: Target): AnswersImportCommand = next(line.with("--target", target.value))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi answers import ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AnswersImportCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi answers import answers.json --target report`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi answers set` command: writes the answer to one question.
 *
 * Building one is writing `katachi answers set ...` in a shell: the methods are named after the
 * options of `katachi answers set --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().answers.set("cq1", "yes").target(report)
 * println(command) // katachi answers set cq1 yes --target report
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi answers set cq1 yes --target report
 * ```
 *
 * @see Katachi
 */
public class AnswersSetCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = AnswersSetCommand(katachi, line)

    /**
     * Same as `katachi answers set --target <report|check-list>` on the command line: the file to read or write.
     *
     * @param target [report] or [checkList].
     */
    public fun target(target: Target): AnswersSetCommand = next(line.with("--target", target.value))

    /**
     * Same as `katachi answers set --note <text>` on the command line: a note to keep with the answer.
     *
     * @param text Any text.
     */
    public fun note(text: String): AnswersSetCommand = next(line.with("--note", text))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi answers set ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): AnswersSetCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi answers set cq1 yes --target report`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
