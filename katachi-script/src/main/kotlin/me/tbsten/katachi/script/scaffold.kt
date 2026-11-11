// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.script.command.ScaffoldCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi scaffold` on the command line, starting from this [Katachi]'s defaults: creates the verification module and adds it to the build.
 */
public val Katachi.scaffold: ScaffoldCommand
    get() = ScaffoldCommand(this, CommandLine(listOf("scaffold")).withNonEmpty("--package", this.defaultPackage).withNonEmpty("--katachi", this.katachiVersion).withNonEmpty("--module-dir", this.moduleDir), DeclarationSite.capture())
