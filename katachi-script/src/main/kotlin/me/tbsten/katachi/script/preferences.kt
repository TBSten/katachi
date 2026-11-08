// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AgentCommandGroup
import me.tbsten.katachi.script.command.PreferencesCommandGroup

/**
 * The subcommands of `katachi agent preferences`: shows and records how the definition is written.
 *
 * `katachi agent preferences` is not a command of its own here: pick one of its subcommands.
 */
public val AgentCommandGroup.preferences: PreferencesCommandGroup
    get() = PreferencesCommandGroup(this.katachi)
