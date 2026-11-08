// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.run

/**
 * The subcommands of `katachi agent data`: reads and writes the JSON embedded in the check list and the report.
 *
 * Each one returns the builder of that subcommand.
 *
 * ## Example 1: pick a subcommand
 *
 * ```kt
 * val command = Katachi().agent.data.get("check-list", "meta.moduleDir")
 * println(command) // katachi agent data get check-list meta.moduleDir
 * command.execute()
 * ```
 *
 * @see Katachi
 */
public class DataCommandGroup internal constructor(private val katachi: Katachi) {
    /**
     * Same as `katachi agent data get <target> [<key>]` on the command line: prints the JSON embedded in the check list or the report, or the part of it at a key.
     *
     * @param target `check-list`, `report` or the path of an HTML file.
     * @param key Dot-separated, such as `meta.moduleDir`. Without it, the whole JSON.
     */
    public fun get(target: String, key: String? = null): DataGetCommand =
        DataGetCommand(katachi, CommandLine(listOf("agent", "data", "get")).withPositionals(listOfNotNull(target, key)))

    /**
     * Same as `katachi agent data set <target> <json>` on the command line: replaces the embedded JSON with the content of a JSON file.
     *
     * @param target `check-list`, `report` or the path of an HTML file.
     * @param json The path of a JSON file.
     */
    public fun set(target: String, json: String): DataSetCommand =
        DataSetCommand(katachi, CommandLine(listOf("agent", "data", "set")).withPositionals(listOf(target, json)))

    /**
     * Same as `katachi agent data merge <target> <json>` on the command line: deep-merges a JSON file into the embedded JSON. Arrays are replaced, not appended to.
     *
     * @param target `check-list`, `report` or the path of an HTML file.
     * @param json The path of a JSON file.
     */
    public fun merge(target: String, json: String): DataMergeCommand =
        DataMergeCommand(katachi, CommandLine(listOf("agent", "data", "merge")).withPositionals(listOf(target, json)))
}

/**
 * The `katachi agent data get` command: prints the JSON embedded in the check list or the report, or the part of it at a key.
 *
 * Building one is writing `katachi agent data get ...` in a shell: the methods are named after the
 * options of `katachi agent data get --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.data.get("check-list", "meta.moduleDir")
 * println(command) // katachi agent data get check-list meta.moduleDir
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent data get check-list meta.moduleDir
 * ```
 *
 * @see Katachi
 */
public class DataGetCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = DataGetCommand(katachi, line)

    /**
     * Same as `katachi agent data get --id <id>` on the command line: the id of the JSON block to read or write. Without it, the one the file is known to have.
     *
     * @param id Such as `checklist`.
     */
    public fun id(id: String): DataGetCommand = next(line.with("--id", id))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent data get ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): DataGetCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent data get check-list meta.moduleDir`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent data set` command: replaces the embedded JSON with the content of a JSON file.
 *
 * Building one is writing `katachi agent data set ...` in a shell: the methods are named after the
 * options of `katachi agent data set --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.data.set("check-list", "meta.json")
 * println(command) // katachi agent data set check-list meta.json
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent data set check-list meta.json
 * ```
 *
 * @see Katachi
 */
public class DataSetCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = DataSetCommand(katachi, line)

    /**
     * Same as `katachi agent data set --id <id>` on the command line: the id of the JSON block to read or write. Without it, the one the file is known to have.
     *
     * @param id Such as `checklist`.
     */
    public fun id(id: String): DataSetCommand = next(line.with("--id", id))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent data set ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): DataSetCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent data set check-list meta.json`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}

/**
 * The `katachi agent data merge` command: deep-merges a JSON file into the embedded JSON. Arrays are replaced, not appended to.
 *
 * Building one is writing `katachi agent data merge ...` in a shell: the methods are named after the
 * options of `katachi agent data merge --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().agent.data.merge("check-list", "meta.json")
 * println(command) // katachi agent data merge check-list meta.json
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi agent data merge check-list meta.json
 * ```
 *
 * @see Katachi
 */
public class DataMergeCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
) : KatachiCommand {
    private fun next(line: CommandLine) = DataMergeCommand(katachi, line)

    /**
     * Same as `katachi agent data merge --id <id>` on the command line: the id of the JSON block to read or write. Without it, the one the file is known to have.
     *
     * @param id Such as `checklist`.
     */
    public fun id(id: String): DataMergeCommand = next(line.with("--id", id))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi agent data merge ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): DataMergeCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int = katachi.run(arguments)

    /** The command line as typed in a shell: `katachi agent data merge check-list meta.json`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
