// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.option

import me.tbsten.katachi.script.en
import me.tbsten.katachi.script.ja

/**
 * The language `katachi agent init --lang` records for the check list, the report and the command's own messages.
 *
 * The top-level [en] and [ja] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.doctor.lang(ja)
 * katachi.doctor.lang(Lang.Ja) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class Lang(public val value: String) {
    /** English (`en`). */
    En("en"),

    /** Japanese (`ja`). */
    Ja("ja"),
}
