package me.tbsten.katachi.script

import kotlin.system.exitProcess
import me.tbsten.katachi.script.internal.ScriptContext
import me.tbsten.katachi.script.internal.argsOfWrapper
import me.tbsten.katachi.script.internal.runScript

/**
 * Runs the `katachi` command with [args] and exits the JVM with its status.
 *
 * `katachi.main.kts` (the file `brew install tbsten/tap/katachi` puts behind the `katachi`
 * command) is this one call. Every subcommand runs here, in this JVM.
 *
 * ## Example 1: the entry point script
 *
 * ```kt
 * @file:DependsOn("me.tbsten.katachi:katachi-script:0.4.0")
 *
 * me.tbsten.katachi.script.main(args)
 * ```
 *
 * @param args The command line after the command name, as `bin/katachi` passes it: `scaffold --package com.example.app`.
 *   An argument `bin/katachi` escaped so `kotlin` would not take it (`-D…`, `-J…`) is turned back here. On Windows,
 *   `bin\katachi.cmd` passes the line as typed in an environment variable instead, and it is split here.
 * @featured
 */
public fun main(args: Array<String>) {
    val status = runScript(argsOfWrapper(args.toList(), System.getenv()), ScriptContext.fromSystem())
    if (status != 0) exitProcess(status)
}
