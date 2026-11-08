// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.script.Katachi

/**
 * The subcommands of `katachi agent`: the steps an AI agent runs as it follows the install guide
 * (`katachi agent init`, `katachi agent check 3-2` and so on).
 *
 * Each one is an extension of this class, named after the subcommand, and returns its builder.
 *
 * ## Example 1: pick a subcommand
 *
 * ```kt
 * val command = Katachi().agent.init.offline().lang(ja)
 * println(command) // katachi agent init --offline --lang ja
 * command.execute()
 * ```
 *
 * @see Katachi
 */
public class AgentCommandGroup internal constructor(internal val katachi: Katachi)
