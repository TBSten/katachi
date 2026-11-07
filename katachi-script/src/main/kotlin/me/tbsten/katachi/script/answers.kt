// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script

import me.tbsten.katachi.script.command.AnswersCommandGroup

/**
 * The subcommands of `katachi answers`: shows and records the answers to the questions.
 *
 * `katachi answers` is not a command of its own here: pick one of its subcommands.
 */
public val Katachi.answers: AnswersCommandGroup
    get() = AnswersCommandGroup(this)
