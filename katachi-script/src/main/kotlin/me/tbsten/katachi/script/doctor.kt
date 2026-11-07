// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.DoctorCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi doctor` on the command line, starting from this [Katachi]'s defaults: checks the JDK Gradle uses, the project's Kotlin version and whether git is there.
 */
public val Katachi.doctor: DoctorCommand
    get() = DoctorCommand(this, CommandLine(listOf("doctor")).withNonEmpty("--lang", this.lang?.value))
