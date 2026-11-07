// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.LintCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi lint` on the command line: lists what in the definition looks off: a Role with files of several kinds, flat groups and the like.
 */
public val Katachi.lint: LintCommand
    get() = LintCommand(this, CommandLine(listOf("lint")))
