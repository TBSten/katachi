// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.GuideCommand
import me.tbsten.katachi.script.internal.CommandLine

/**
 * Same as `katachi guide` on the command line, starting from this [Katachi]'s defaults: prints the install guide one section at a time, or its table of contents without a section.
 */
public val Katachi.guide: GuideCommand
    get() = GuideCommand(this, CommandLine(listOf("guide")).withNonEmpty("--lang", this.lang?.value))

/**
 * Same as `katachi guide <section>` on the command line, starting from this [Katachi]'s defaults: prints the install guide one section at a time, or its table of contents without a section.
 *
 * ## Example 1: pass the arguments
 *
 * ```kt
 * val command = Katachi().guide("3-2")
 * println(command) // katachi guide 3-2
 * ```
 *
 * @param section The number of a heading, such as `3-2`.
 */
public fun Katachi.guide(section: String): GuideCommand =
    GuideCommand(this, CommandLine(listOf("guide")).withNonEmpty("--lang", this.lang?.value).withPositionals(listOf(section)))
