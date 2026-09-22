package me.tbsten.katachi.scan

/**
 * One labelled value a report block states about a violation katachi did not write.
 *
 * A value, not a sentence: `ViolationDetail("Rule", "LongMethod")`, never
 * `ViolationDetail("", "The rule LongMethod was violated.")`.
 *
 * A `data class` rather than a plain one, unlike [NearbyLocation]: this is a value **you**
 * construct, so `violation.details shouldBe listOf(ViolationDetail("Rule", "X"))` has to work
 * in your own tests.
 *
 * ## Example 1: state which rule a third party check failed on
 * ```kt
 * ViolationDetail("Rule", "LongMethod").toString() shouldBe "Rule: LongMethod"
 * ```
 */
public data class ViolationDetail(
    /**
     * What the value is about, e.g. `"Rule"`.
     *
     * ## Example 1: read what a detail's value is about
     * ```kt
     * ViolationDetail("Rule", "LongMethod").label shouldBe "Rule"
     * ```
     */
    public val label: String,
    /**
     * The value itself, e.g. `"LongMethod"`.
     *
     * ## Example 1: read a detail's value
     * ```kt
     * ViolationDetail("Rule", "LongMethod").value shouldBe "LongMethod"
     * ```
     */
    public val value: String,
) {
    override fun toString(): String = "$label: $value"
}
