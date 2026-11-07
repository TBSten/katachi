// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.CompareViolationsCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi compare-violations` on the command line: compares the violations of two test logs, as before and after a refactoring of the definition.
 */
public val Katachi.`compare-violations`: CompareViolationsCommand
    get() = CompareViolationsCommand(this, CommandLine(listOf("compare-violations")))

/**
 * Same as `katachi compare-violations <before> [<after>]` on the command line: compares the violations of two test logs, as before and after a refactoring of the definition.
 *
 * ## Example 1: pass the arguments
 *
 * ```kt
 * val command = Katachi().`compare-violations`("before.log", "after.log")
 * println(command) // katachi compare-violations before.log after.log
 * ```
 *
 * @param before The log before. Without it, `tmp/test-before-refactor.log` of the working directory.
 * @param after The log after. Without it, `tmp/test-after-refactor.log` of the working directory.
 */
public fun Katachi.`compare-violations`(before: String, after: String? = null): CompareViolationsCommand =
    CompareViolationsCommand(this, CommandLine(listOf("compare-violations")).withPositionals(listOfNotNull(before, after)))
