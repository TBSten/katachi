// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.CheckCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi check <items>...` on the command line: marks items of the check list done.
 *
 * ## Example 1: pass the arguments
 *
 * ```kt
 * val command = Katachi().check("3-2")
 * println(command) // katachi check 3-2
 * ```
 *
 * @param items The ids of the items, such as `3-2`.
 */
public fun Katachi.check(vararg items: String): CheckCommand =
    CheckCommand(this, CommandLine(listOf("check")).withPositionals(items.toList()))
