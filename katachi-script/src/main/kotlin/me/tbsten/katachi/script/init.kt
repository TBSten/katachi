// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AgentCommandGroup
import me.tbsten.katachi.script.command.InitCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi agent init` on the command line, starting from this [Katachi]'s defaults: makes the working directory and lays out the check list and the report.
 */
public val AgentCommandGroup.init: InitCommand
    get() = InitCommand(this.katachi, CommandLine(listOf("agent", "init")).withNonEmpty("--workdir", this.katachi.workdir).withNonEmpty("--katachi", this.katachi.katachiVersion).withNonEmpty("--module-dir", this.katachi.moduleDir).withNonEmpty("--lang", this.katachi.lang?.value))
