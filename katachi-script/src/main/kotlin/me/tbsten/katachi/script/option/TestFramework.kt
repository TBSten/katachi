// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.option

import me.tbsten.katachi.script.junit5
import me.tbsten.katachi.script.kotest
import me.tbsten.katachi.script.kotestAnnotation
import me.tbsten.katachi.script.kotestBehavior
import me.tbsten.katachi.script.kotestDescribe
import me.tbsten.katachi.script.kotestExpect
import me.tbsten.katachi.script.kotestFeature
import me.tbsten.katachi.script.kotestFree
import me.tbsten.katachi.script.kotestFun
import me.tbsten.katachi.script.kotestShould
import me.tbsten.katachi.script.kotestString
import me.tbsten.katachi.script.kotestWord

/**
 * The test framework `katachi scaffold --test-framework` lays the verification module out for.
 *
 * The top-level [junit5], [kotest], [kotestFun], [kotestString], [kotestShould], [kotestDescribe], [kotestBehavior], [kotestWord], [kotestFree], [kotestFeature], [kotestExpect] and [kotestAnnotation] are the same values, so a script can write them bare.
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

    /** Kotest, with the test written as a `FunSpec` like `kotest-fun` (`kotest`). */
    Kotest("kotest"),

    /** Kotest `FunSpec`: `test("...") { }` (`kotest-fun`). */
    KotestFun("kotest-fun"),

    /** Kotest `StringSpec`: `"..." { }` (`kotest-string`). */
    KotestString("kotest-string"),

    /** Kotest `ShouldSpec`: `should("...") { }` (`kotest-should`). */
    KotestShould("kotest-should"),

    /** Kotest `DescribeSpec`: `describe("...") { it("...") { } }` (`kotest-describe`). */
    KotestDescribe("kotest-describe"),

    /** Kotest `BehaviorSpec`: `Given("...") { Then("...") { } }` (`kotest-behavior`). */
    KotestBehavior("kotest-behavior"),

    /** Kotest `WordSpec`: `"..." should { "..." { } }` (`kotest-word`). */
    KotestWord("kotest-word"),

    /** Kotest `FreeSpec`: `"..." { }` (`kotest-free`). */
    KotestFree("kotest-free"),

    /** Kotest `FeatureSpec`: `feature("...") { scenario("...") { } }` (`kotest-feature`). */
    KotestFeature("kotest-feature"),

    /** Kotest `ExpectSpec`: `context("...") { expect("...") { } }` (`kotest-expect`). */
    KotestExpect("kotest-expect"),

    /** Kotest `AnnotationSpec`: a `@Test` function, as in JUnit (`kotest-annotation`). */
    KotestAnnotation("kotest-annotation"),
}
