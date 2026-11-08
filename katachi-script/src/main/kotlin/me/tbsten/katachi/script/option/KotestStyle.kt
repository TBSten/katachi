// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.option

/**
 * The spec style `katachi scaffold --kotest-style` writes the Kotest test in.
 *
 * ## Example 1: pass one to the command
 *
 * ```kt
 * katachi.scaffold.`kotest-style`(KotestStyle.FunSpec)
 * ```
 *
 * @property value How the command line spells it.
 */
public enum class KotestStyle(public val value: String) {
    /** `FunSpec`, the default: `test("...") { }` (`fun-spec`). */
    FunSpec("fun-spec"),

    /** `StringSpec`: `"..." { }` (`string-spec`). */
    StringSpec("string-spec"),

    /** `ShouldSpec`: `should("...") { }` (`should-spec`). */
    ShouldSpec("should-spec"),

    /** `DescribeSpec`: `describe("...") { it("...") { } }` (`describe-spec`). */
    DescribeSpec("describe-spec"),

    /** `BehaviorSpec`: `Given("...") { Then("...") { } }` (`behavior-spec`). */
    BehaviorSpec("behavior-spec"),

    /** `WordSpec`: `"..." should { "..." { } }` (`word-spec`). */
    WordSpec("word-spec"),

    /** `FreeSpec`: `"..." { }` (`free-spec`). */
    FreeSpec("free-spec"),

    /** `FeatureSpec`: `feature("...") { scenario("...") { } }` (`feature-spec`). */
    FeatureSpec("feature-spec"),

    /** `ExpectSpec`: `context("...") { expect("...") { } }` (`expect-spec`). */
    ExpectSpec("expect-spec"),

    /** `AnnotationSpec`: a `@Test` function, as in JUnit (`annotation-spec`). */
    AnnotationSpec("annotation-spec"),
}
