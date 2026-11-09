// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.script.Katachi

/**
 * The subcommands of `katachi agent`: what an AI agent runs for the katachi skill's steps
 * (`katachi agent test` and `katachi agent compare-violations`).
 *
 * Each one is an extension of this class, named after the subcommand, and returns its builder.
 *
 * ## Example 1: pick a subcommand
 *
 * ```kt
 * val command = Katachi().agent.test.save("before-refactor")
 * println(command) // katachi agent test --save before-refactor
 * command.execute()
 * ```
 *
 * @see Katachi
 */
public class AgentCommandGroup internal constructor(internal val katachi: Katachi)
