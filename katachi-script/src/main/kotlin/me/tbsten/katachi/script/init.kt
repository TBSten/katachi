// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.InitCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi init` on the command line, starting from this [Katachi]'s defaults: makes the working directory and lays out the check list and the report.
 */
public val Katachi.init: InitCommand
    get() = InitCommand(this, CommandLine(listOf("init")).withNonEmpty("--workdir", this.workdir).withNonEmpty("--katachi", this.katachiVersion).withNonEmpty("--module-dir", this.moduleDir).withNonEmpty("--lang", this.lang?.value))
