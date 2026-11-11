// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AgentCommandGroup
import me.tbsten.katachi.script.command.TestCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi agent test` on the command line: runs the tests of the verification module and prints only a summary.
 */
public val AgentCommandGroup.test: TestCommand
    get() = TestCommand(this.katachi, CommandLine(listOf("agent", "test")))
