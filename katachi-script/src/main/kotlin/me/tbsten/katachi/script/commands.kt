package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.Commands
import me.tbsten.katachi.script.command.KatachiCommand

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
