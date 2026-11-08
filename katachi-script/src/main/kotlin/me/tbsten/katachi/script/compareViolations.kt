// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AgentCommandGroup
import me.tbsten.katachi.script.command.CompareViolationsCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi agent compare-violations` on the command line: compares the violations of two test logs, as before and after a refactoring of the definition.
 */
public val AgentCommandGroup.`compare-violations`: CompareViolationsCommand
    get() = CompareViolationsCommand(this.katachi, CommandLine(listOf("agent", "compare-violations")))

/**
 * Same as `katachi agent compare-violations <before> [<after>]` on the command line: compares the violations of two test logs, as before and after a refactoring of the definition.
 *
 * ## Example 1: pass the arguments
 *
 * ```kt
 * val command = Katachi().agent.`compare-violations`("before.log", "after.log")
 * println(command) // katachi agent compare-violations before.log after.log
 * ```
 *
 * @param before The log before. Without it, `tmp/test-before-refactor.log` of the working directory.
 * @param after The log after. Without it, `tmp/test-after-refactor.log` of the working directory.
 */
public fun AgentCommandGroup.`compare-violations`(before: String, after: String? = null): CompareViolationsCommand =
    CompareViolationsCommand(this.katachi, CommandLine(listOf("agent", "compare-violations")).withPositionals(listOfNotNull(before, after)))
