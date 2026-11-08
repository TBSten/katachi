// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.option

import me.tbsten.katachi.script.checkList
import me.tbsten.katachi.script.report

/**
 * Which file `katachi agent answers --target` reads or writes.
 *
 * The top-level [report] and [checkList] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.agent.answers.show.target(report)
 * katachi.agent.answers.show.target(Target.Report) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class Target(public val value: String) {
    /** The report (`report`). */
    Report("report"),

    /** The check list (`check-list`). */
    CheckList("check-list"),
}
