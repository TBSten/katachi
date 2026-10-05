package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import java.time.Instant

/**
 * When the gutter marks its explanations `(stale)` and when it asks the daemon to draw again (design 2.6, U-13).
 * Pure: the line marker provider and the highlighting refresher give it the
 * states and times they read.
 *
 * ```kotlin
 * val stale = GutterFreshness.isStale(service.state.value, roles.freshness, file.timeStamp, generatedAt, unsaved)
 * if (GutterFreshness.shouldRedraw(before, after)) restartOpenFiles()
 * ```
 */
internal object GutterFreshness {
    /**
     * Whether the answer may be older than what the user sees: the definition is [Freshness.Stale], it could not be
     * read again ([DefinitionState.Unavailable] still carrying the last answer), the file was written after the
     * definition ([fileModifiedAt] after [generatedAt]), or it has edits not saved yet. `Loading` alone is not stale.
     */
    fun isStale(state: DefinitionState, freshness: Freshness, fileModifiedAt: Instant?, generatedAt: Instant?, unsaved: Boolean): Boolean =
        freshness is Freshness.Stale ||
            state is DefinitionState.Unavailable ||
            unsaved ||
            (fileModifiedAt != null && generatedAt != null && fileModifiedAt.isAfter(generatedAt))

    /**
     * Whether a change of the definition's state is worth drawing the open files again. Going to `Loading` is not:
     * the previous answer stays on screen until the new one arrives (design 2.6, the foundation's K-1).
     */
    fun shouldRedraw(before: DefinitionState?, after: DefinitionState): Boolean =
        after !is DefinitionState.Loading && before != after
}
