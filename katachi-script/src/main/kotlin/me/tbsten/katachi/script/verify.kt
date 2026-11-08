// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AgentCommandGroup
import me.tbsten.katachi.script.command.VerifyCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi agent verify` on the command line: lists what is left to fill in, and closes the last step when nothing is.
 */
public val AgentCommandGroup.verify: VerifyCommand
    get() = VerifyCommand(this.katachi, CommandLine(listOf("agent", "verify")))
