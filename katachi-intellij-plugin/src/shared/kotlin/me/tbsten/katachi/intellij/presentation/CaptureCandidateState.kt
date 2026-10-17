package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ParameterModel

/**
 * The directory names offered for one capture field, as the screens keep them: at most [MAX] [values]
 * and how many more there were ([hiddenCount]). [directoryPattern] is where a value would land, the capture
 * written `{value}` (`feature/{value}`), so a value that is not among the candidates can say it makes a new directory.
 *
 * ```kotlin
 * CandidateList(listOf("home", "settings"), hiddenCount = 0, directoryPattern = "feature/{value}")
 * ```
 */
internal data class CandidateList(
    val values: List<String>,
    val hiddenCount: Int = 0,
    val directoryPattern: String? = null,
) {
    companion object {
        /** Candidates shown at most; more are counted in [hiddenCount]. */
        const val MAX: Int = 100

        /** The placeholder [directoryPattern] holds in place of the field's own value. */
        const val VALUE_MARK: String = "{value}"
    }
}

/**
 * What a capture field draws when it offers candidates (design 3.4): the field is then a combo box, not a plain
 * text field. `null` in [FieldUi.Text.suggestions] is the plain field.
 */
internal data class SuggestionsUi(
    val values: List<String>,
    /** "{n} more" when candidates were cut off; `null` when none were. */
    val moreLabel: String?,
    /** The faint note under the field for a value that is not a candidate (not an error); `null` otherwise. */
    val newDirectoryNote: String?,
)

/** The words of a candidate field, apart from the rest of a screen's texts so that both screens word them from their own strings. */
internal interface CandidateStrings {
    /** The note for a value that is no existing directory: [path] is where it would land (`feature/profile`). */
    fun newDirectoryNote(path: String): String

    /** "{count} more": candidates cut off after [CandidateList.MAX]. */
    fun moreCandidates(count: Int): String
}

/**
 * The suggestions of [capture] holding [input], or `null` for a plain field.
 *
 * - A module capture the JSON lists modules for ([ParameterModel.CaptureParam.existingModules]) offers those modules, and
 *   a value outside them is the validation error, not a new directory (katachi refuses it).
 * - Otherwise the directories read from the project ([candidates]); a value that is none of them gets the
 *   "new directory" note unless it has an [error] or the list was cut off (it may be one of the hidden ones).
 */
internal fun suggestionsOf(
    capture: ParameterModel.CaptureParam,
    candidates: CandidateList?,
    input: String?,
    error: String?,
    strings: CandidateStrings,
): SuggestionsUi? {
    val modules = capture.existingModules
    if (modules != null) {
        if (modules.isEmpty()) return null
        val hidden = (modules.size - CandidateList.MAX).coerceAtLeast(0)
        return SuggestionsUi(modules.take(CandidateList.MAX), hidden.takeIf { it > 0 }?.let(strings::moreCandidates), newDirectoryNote = null)
    }
    if (candidates == null || candidates.values.isEmpty()) return null
    val value = input.orEmpty()
    val note = candidates.directoryPattern
        ?.takeIf { value.isNotBlank() && error == null && candidates.hiddenCount == 0 && value !in candidates.values }
        ?.let { strings.newDirectoryNote(it.replace(CandidateList.VALUE_MARK, value)) }
    return SuggestionsUi(
        values = candidates.values,
        moreLabel = candidates.hiddenCount.takeIf { it > 0 }?.let(strings::moreCandidates),
        newDirectoryNote = note,
    )
}
