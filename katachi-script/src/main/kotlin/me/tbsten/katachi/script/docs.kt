// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.DocsCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi docs` on the command line: fetches the katachi guide into the working directory and prints the path.
 */
public val Katachi.docs: DocsCommand
    get() = DocsCommand(this, CommandLine(listOf("docs")))
