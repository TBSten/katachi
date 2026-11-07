// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.option

/**
 * The module whose API reference `katachi docs --api` fetches.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.docs.api(ApiModule.Katachi)
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class ApiModule(public val value: String) {
    /** katachi itself (`katachi`). */
    Katachi("katachi"),

    /** katachi-konsist (`katachi-konsist`). */
    KatachiKonsist("katachi-konsist"),
}
