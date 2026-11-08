// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AgentCommandGroup
import me.tbsten.katachi.script.command.AnswersCommandGroup

/**
 * The subcommands of `katachi agent answers`: shows and records the answers to the questions.
 *
 * `katachi agent answers` is not a command of its own here: pick one of its subcommands.
 */
public val AgentCommandGroup.answers: AnswersCommandGroup
    get() = AnswersCommandGroup(this.katachi)
