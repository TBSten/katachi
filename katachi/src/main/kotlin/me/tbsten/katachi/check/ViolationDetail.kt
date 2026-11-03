package me.tbsten.katachi.check

/**
 * One labelled value a report block states about a violation katachi did not write.
 *
 * A value, not a sentence: `ViolationDetail("Rule", "LongMethod")`, never
 * `ViolationDetail("", "The rule LongMethod was violated.")`.
 *
 * It is a `data class`, so `violation.details shouldBe listOf(ViolationDetail("Rule", "X"))`
 * works in your own tests.
 *
 * ## Example 1: state which rule a third party check failed on
 * ```kt
 * val detail = ViolationDetail("Rule", "LongMethod")
 * detail.label shouldBe "Rule"
 * detail.value shouldBe "LongMethod"
 * detail.toString() shouldBe "Rule: LongMethod"
 * ```
 *
 * @constructor Takes the label and the value, each as the report should show it.
 */
public data class ViolationDetail(
    /** What the value is about, e.g. `"Rule"`. */
    public val label: String,
    /** The value itself, e.g. `"LongMethod"`. */
    public val value: String,
) {
    /** `<label>: <value>`, the way a report prints the row. */
    override fun toString(): String = "$label: $value"
}
