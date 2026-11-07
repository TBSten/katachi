// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.option

import me.tbsten.katachi.script.junit5
import me.tbsten.katachi.script.kotest

/**
 * The test framework `katachi scaffold --test-framework` lays the test module out for.
 *
 * The top-level [junit5] and [kotest] are the same values, so a script can write them bare.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.scaffold.`test-framework`(kotest)
 * katachi.scaffold.`test-framework`(TestFramework.Kotest) // the same
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class TestFramework(public val value: String) {
    /** JUnit 5 (`junit5`). */
    Junit5("junit5"),

    /** Kotest (`kotest`). */
    Kotest("kotest"),
}
