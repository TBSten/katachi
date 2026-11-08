// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AddCommandGroup
import me.tbsten.katachi.script.command.AgentCommandGroup

/**
 * The subcommands of `katachi agent add`: appends one row to an array of the check list or the report, without touching the others.
 *
 * `katachi agent add` is not a command of its own here: pick one of its subcommands.
 */
public val AgentCommandGroup.add: AddCommandGroup
    get() = AddCommandGroup(this.katachi)
