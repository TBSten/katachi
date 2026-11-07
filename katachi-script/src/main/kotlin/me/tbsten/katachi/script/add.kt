// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AddCommandGroup

/**
 * The subcommands of `katachi add`: appends one row to an array of the check list or the report, without touching the others.
 *
 * `katachi add` is not a command of its own here: pick one of its subcommands.
 */
public val Katachi.add: AddCommandGroup
    get() = AddCommandGroup(this)
