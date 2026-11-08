// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.option

import me.tbsten.katachi.script.numbered
import me.tbsten.katachi.script.plain

/**
 * Whether `katachi agent preferences set constraintNames=...` puts the question number in constraint names.
 *
 * The top-level [plain] and [numbered] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.agent.preferences.set.constraintNames(plain)
 * katachi.agent.preferences.set.constraintNames(ConstraintNames.Plain) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class ConstraintNames(public val value: String) {
    /** Names only (`plain`). */
    Plain("plain"),

    /** Names with the question number (`numbered`). */
    Numbered("numbered"),
}
