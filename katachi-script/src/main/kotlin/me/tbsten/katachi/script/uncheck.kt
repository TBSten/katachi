// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AgentCommandGroup
import me.tbsten.katachi.script.command.UncheckCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi agent uncheck <items>...` on the command line: marks items of the check list not done.
 *
 * ## Example 1: pass the arguments
 *
 * ```kt
 * val command = Katachi().agent.uncheck("3-2")
 * println(command) // katachi agent uncheck 3-2
 * ```
 *
 * @param items The ids of the items, such as `3-2`.
 */
public fun AgentCommandGroup.uncheck(vararg items: String): UncheckCommand =
    UncheckCommand(this.katachi, CommandLine(listOf("agent", "uncheck")).withPositionals(items.toList()))
