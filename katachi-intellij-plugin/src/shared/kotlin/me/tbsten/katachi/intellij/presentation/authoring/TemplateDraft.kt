package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.data.definition.CapturedValue

/**
 * The text of a role's file written as the body of a `katachiTemplate` (ide-live-template-from-definition): the file's text with
 * each value a `capture(...)` took from its path put back as `${name}`, to go between the `"""` of the template function.
 *
 * ```kotlin
 * TemplateDrafts.bodyOf("class UserService", listOf(CapturedValue("name", "User")))   // "class ${name}Service"
 * ```
 */
internal object TemplateDrafts {
    /** A file longer than this is not made into a draft: a template of that size is the author's to cut from the file. */
    const val LINES_MAX: Int = 120

    /** The shortest value that is taken for a capture: `a` in a file is more often a letter than the capture. */
    private const val VALUE_LENGTH_MIN: Int = 2

    /** `null` when the text cannot be a raw string (it holds `"""`) or is too long. */
    fun bodyOf(text: String, captures: List<CapturedValue>): String? {
        val lines = text.trimEnd().lines()
        if (lines.isEmpty() || lines.size > LINES_MAX || "\"\"\"" in text) return null
        // The longest value first, so that `UserProfile` is not cut in two by a capture `User`.
        val values = captures.filter { it.value.length >= VALUE_LENGTH_MIN }.sortedByDescending { it.value.length }
        // One pass over each line, so that a value found inside what an earlier one was replaced with (`name` in `${name}`) is left alone.
        val pattern = values.takeIf { it.isNotEmpty() }?.joinToString("|") { Regex.escape(it.value) }?.let(::Regex)
        return lines.joinToString("\n") { line -> replaced(escaped(line), pattern, values) }
    }

    /** A `$` in the file would start a reference in the raw string. */
    private fun escaped(line: String): String = line.replace("$", "\${'$'}")

    private fun replaced(line: String, pattern: Regex?, values: List<CapturedValue>): String =
        pattern?.replace(line) { match -> "\${${values.first { it.value == match.value }.name}}" } ?: line
}
