// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.WarnCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi warn <step> [<text>]` on the command line: puts a warning on a step of the check list, which keeps it open even when it is done.
 *
 * ## Example 1: pass the arguments
 *
 * ```kt
 * val command = Katachi().warn("4", "gradlew is missing")
 * println(command) // katachi warn 4 'gradlew is missing'
 * ```
 *
 * @param step The id of the step, such as `4`.
 * @param text The warning. Leave it out with `.clear()`.
 */
public fun Katachi.warn(step: String, text: String? = null): WarnCommand =
    WarnCommand(this, CommandLine(listOf("warn")).withPositionals(listOfNotNull(step, text)))
