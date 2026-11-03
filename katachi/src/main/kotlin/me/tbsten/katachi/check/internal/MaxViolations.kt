package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.DEFAULT_MAX_VIOLATIONS

internal const val MAX_VIOLATIONS_PROPERTY: String = "katachi.maxViolations"

internal const val MAX_VIOLATIONS_ALL: String = "all"

internal const val TRUNCATION_HINT: String =
    "To show every block, run with -D$MAX_VIOLATIONS_PROPERTY=$MAX_VIOLATIONS_ALL, or set maxViolations in assert()."

internal fun resolveMaxViolations(
    explicit: Int?,
    systemProperty: (String) -> String?,
    standardError: (String) -> Unit,
): Int {
    val property = systemProperty(MAX_VIOLATIONS_PROPERTY)
    if (explicit != null) {
        if (property != null) standardError(explicitWinsNotice(property, explicit))
        return explicit
    }
    if (property == null) return DEFAULT_MAX_VIOLATIONS
    val value = property.trim()
    if (value.equals(MAX_VIOLATIONS_ALL, ignoreCase = true)) return Int.MAX_VALUE
    val parsed = value.toIntOrNull()
    if (parsed != null && parsed >= 0) return parsed
    standardError(invalidValueWarning(property))
    return DEFAULT_MAX_VIOLATIONS
}

private fun invalidValueWarning(property: String): String =
    "Warning: -D$MAX_VIOLATIONS_PROPERTY=${oneLine(property)} is neither \"$MAX_VIOLATIONS_ALL\" nor a whole " +
        "number of 0 or more, so it was ignored and the default of $DEFAULT_MAX_VIOLATIONS was used. " +
        "Give -D$MAX_VIOLATIONS_PROPERTY=$MAX_VIOLATIONS_ALL to show every block."

private fun explicitWinsNotice(property: String, explicit: Int): String =
    "Warning: -D$MAX_VIOLATIONS_PROPERTY=${oneLine(property)} was ignored because assert() sets " +
        "maxViolations = $explicit, which wins. Drop maxViolations from the test to set the budget " +
        "from the command line."
