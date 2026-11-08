// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AgentCommandGroup

/**
 * The subcommands of `katachi agent`: the steps an AI agent runs as it follows the install guide.
 *
 * `katachi agent` is not a command of its own here: pick one of its subcommands.
 */
public val Katachi.agent: AgentCommandGroup
    get() = AgentCommandGroup(this)
