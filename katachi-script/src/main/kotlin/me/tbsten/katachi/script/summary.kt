// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.SummaryCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi summary` on the command line: prints the message to hand back to the user.
 */
public val Katachi.summary: SummaryCommand
    get() = SummaryCommand(this, CommandLine(listOf("summary")))
