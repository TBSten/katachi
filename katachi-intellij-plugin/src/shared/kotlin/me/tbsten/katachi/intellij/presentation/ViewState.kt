package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId

/** A "▸ Details" the user can open: under the load error, or under a failed row of the result. */
internal sealed interface DetailsKey {
    data object LoadError : DetailsKey

    data class ResultRow(val templateId: TemplateId) : DetailsKey
}

/** "Show cause" of a row whose preview failed (E-07): the `katachiTemplates` text output. */
internal sealed interface CauseState {
    data object Loading : CauseState

    data class Loaded(val lines: List<String>) : CauseState
}

/**
 * What the screen shows beyond the list and the form: open/closed parts and facts the IDE side
 * feeds in (stage 3). Kept apart from [FormState] because none of it is ever sent to Gradle.
 */
internal data class ViewState(
    /** Module header bands folded by the user. Remembered per project (spec 04). */
    val collapsedModules: Set<ModuleId> = emptySet(),
    val openDetails: Set<DetailsKey> = emptySet(),
    /** String fields widened to several lines with ▸. */
    val multilineFields: Set<FieldId> = emptySet(),
    val causes: Map<TemplateId, CauseState> = emptyMap(),
    /** The definition changed since the last load (E-44): the "reload" banner. */
    val definitionChanged: Boolean = false,
    /** Checked rows outside the search whose form the disabled-reason line opened anyway (E-16). */
    val revealedRows: Set<TemplateId> = emptySet(),
    /** The row [View template] of an editor notification asked for (C2); it stays until the next one. */
    val highlight: Highlight? = null,
    /**
     * How many times the IDE asked for the search field's focus (T-3, `GenerateFromTemplateAction`). A number, like
     * [Highlight.sequence], so asking again focuses again, and a list shown after the request (loading, an empty state)
     * still takes it once.
     */
    val focusRequest: Int = 0,
    /** The band "the gutter needs katachi 0.3" (T-8) was closed with ✕. Kept while the project is open, not saved. */
    val tooOldDismissed: Boolean = false,
)

/**
 * The highlighted row. [sequence] counts the requests, so that asking for the same row again
 * scrolls to it again, and a tool window created after the request still scrolls to it once.
 */
internal data class Highlight(val templateId: TemplateId, val sequence: Int)

/** Applies an intent that only opens or closes a part of the screen; `null` for any other intent. */
internal fun applyViewIntent(view: ViewState, intent: KatachiIntent): ViewState? = when (intent) {
    is KatachiIntent.ToggleModule -> view.copy(collapsedModules = view.collapsedModules.toggle(intent.moduleId))
    is KatachiIntent.ToggleDetails -> view.copy(openDetails = view.openDetails.toggle(intent.key))
    is KatachiIntent.ToggleMultiline -> view.copy(multilineFields = view.multilineFields.toggle(intent.field))
    KatachiIntent.FocusSearch -> view.copy(focusRequest = view.focusRequest + 1)
    KatachiIntent.DismissDefinitionChanged -> view.copy(definitionChanged = false)
    KatachiIntent.DismissTooOld -> view.copy(tooOldDismissed = true)
    KatachiIntent.DefinitionChanged -> view.copy(definitionChanged = true)
    else -> null
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
