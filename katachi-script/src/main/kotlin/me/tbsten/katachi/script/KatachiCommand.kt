package me.tbsten.katachi.script

/**
 * One `katachi` command line, ready to print or to run.
 *
 * `toString()` is the line as you would type it in a shell, starting with `katachi`.
 *
 * ## Example 1: print a command, then run it
 *
 * ```kt
 * val scaffold = katachi.scaffold.`package`("com.example.app")
 * println(scaffold) // katachi scaffold --package com.example.app
 * val status = scaffold.execute()
 * ```
 *
 * @see Katachi
 * @see Commands
 */
public interface KatachiCommand {
    /** Everything after `katachi`: `["scaffold", "--package", "com.example.app"]`. */
    public val arguments: List<String>

    /**
     * Runs the command with [Katachi.runner] and returns its exit code.
     *
     * @throws KatachiMissingCommandOptionException when an option the subcommand requires is not set.
     */
    public fun execute(): Int
}

/**
 * Commands that run one after another.
 *
 * `toString()` puts one command per line, so the whole list can be pasted into a shell.
 *
 * ## Example 1: run init, then scaffold
 *
 * ```kt
 * commands(
 *     katachi.init.lang(ja),
 *     katachi.scaffold.`package`("com.example.app"),
 * ).also {
 *     println(it)
 *     it.execute()
 * }
 * ```
 *
 * @property commands The commands, in the order they run.
 * @constructor Takes the commands in the order they run; [commands] is the shorter way to write it.
 * @see commands
 * @see executeCommands
 */
public class Commands(public val commands: List<KatachiCommand>) {
    /**
     * Runs the commands in order and stops at the first one that fails.
     *
     * @return 0 when every command succeeded, otherwise the exit code of the one that failed.
     */
    public fun execute(): Int {
        for (command in commands) {
            val status = command.execute()
            if (status != 0) return status
        }
        return 0
    }

    /** The command lines, one per line, as typed in a shell. */
    override fun toString(): String = commands.joinToString("\n")
}

/**
 * Puts [commands] in a [Commands] to print or run together.
 *
 * ## Example 1: show what the script will run
 *
 * ```kt
 * println(commands(katachi.init, katachi.scaffold.`package`("com.example.app")))
 * // katachi init
 * // katachi scaffold --package com.example.app
 * ```
 *
 * @param commands The commands, in the order they run.
 */
public fun commands(vararg commands: KatachiCommand): Commands = Commands(commands.toList())

/**
 * Runs [commands] in order and stops at the first one that fails: `commands(...).execute()`.
 *
 * ## Example 1: end the script with the status of the commands
 *
 * ```kt
 * val status = executeCommands(
 *     katachi.init.lang(ja),
 *     katachi.scaffold.`package`("com.example.app"),
 * )
 * kotlin.system.exitProcess(status)
 * ```
 *
 * @param commands The commands, in the order they run.
 * @return 0 when every command succeeded, otherwise the exit code of the one that failed.
 */
public fun executeCommands(vararg commands: KatachiCommand): Int = commands(*commands).execute()
