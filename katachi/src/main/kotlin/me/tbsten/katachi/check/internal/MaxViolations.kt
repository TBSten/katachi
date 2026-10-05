package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.DEFAULT_MAX_VIOLATIONS

/** The system property that sets the budget for a run: `all`, or a whole number of 0 or more. */
internal const val MAX_VIOLATIONS_PROPERTY: String = "katachi.maxViolations"

/** The value of -Dkatachi.maxViolations that means "every block". Read ignoring case. */
internal const val MAX_VIOLATIONS_ALL: String = "all"

/**
 * The line a report adds right after its truncation line, saying how to see the rest. The same
 * text whatever the budget came from: a `-D` reaches `katachi<Key>` tasks too, through the
 * Gradle plugin, so the advice never lies about where it works.
 */
internal const val TRUNCATION_HINT: String =
    "To show every block, run with -D$MAX_VIOLATIONS_PROPERTY=$MAX_VIOLATIONS_ALL, or set maxViolations in assert()."

/**
 * The budget a run prints with: [explicit] when the test gave one, else the system property,
 * else [DEFAULT_MAX_VIOLATIONS]. A property that is neither `all` nor a whole number of 0 or
 * more is reported on [standardError] and read as not given. A property given next to an
 * [explicit] is reported on [standardError] too, so that a `-D` the test's own value beat is
 * never ignored in silence.
 *
 * No default for [systemProperty] or [standardError] on purpose: the real `System.getProperty`
 * and `System.err` are handed in by the three call sites whose message reaches the user, and a
 * default here would be one more path that reads the real property by accident.
 */
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
