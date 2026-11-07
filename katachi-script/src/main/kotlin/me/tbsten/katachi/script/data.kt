// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.DataCommandGroup

/**
 * The subcommands of `katachi data`: reads and writes the JSON embedded in the check list and the report.
 *
 * `katachi data` is not a command of its own here: pick one of its subcommands.
 */
public val Katachi.data: DataCommandGroup
    get() = DataCommandGroup(this)
