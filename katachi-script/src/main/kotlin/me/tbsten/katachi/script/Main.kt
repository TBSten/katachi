package me.tbsten.katachi.script

import kotlin.system.exitProcess
import me.tbsten.katachi.script.internal.ScriptContext
import me.tbsten.katachi.script.internal.runScript

/**
 * Runs the `katachi` command with [args] and exits the JVM with its status.
 *
 * `katachi.main.kts` (the file `brew install tbsten/tap/katachi` puts behind the `katachi`
 * command) is this one call. Every subcommand runs here, in this JVM; the guide and the templates
 * `init` places ship inside this artifact, so nothing is downloaded but what `docs` fetches.
 *
 * ## Example 1: the entry point script
 *
 * ```kt
 * @file:DependsOn("me.tbsten.katachi:katachi-script:0.4.0")
 *
 * me.tbsten.katachi.script.main(args)
 * ```
 *
 * @param args The command line after the command name: `data get check-list meta.moduleDir`.
 * @featured
 */
public fun main(args: Array<String>) {
    val status = runScript(args.toList(), ScriptContext.fromSystem())
    if (status != 0) exitProcess(status)
}
