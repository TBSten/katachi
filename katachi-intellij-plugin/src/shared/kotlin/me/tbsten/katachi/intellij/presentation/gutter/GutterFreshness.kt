package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
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

    /**
     * Whether a ◆ that sees [state] asks the service to read the definition (design 2.6, `NotLoaded`): nothing was asked yet
     * and the user lets katachi load by itself ([autoLoad], `autoLoadTemplates`), or it was off when the service looked and is
     * on again. The same rule as the editor notification's.
     */
    fun shouldEnsureLoaded(state: DefinitionState, autoLoad: Boolean): Boolean =
        autoLoad && (
            state == DefinitionState.NotLoaded ||
                (state is DefinitionState.Unavailable && state.reason == DefinitionUnavailableReason.LoadingWithoutUserDisabled)
            )

    /**
     * Whether the definition was written by a katachi older than the IDE reads (design 2.6, `KatachiTooOld`): the gutter and the
     * annotations draw nothing, though the state may still carry an earlier answer.
     */
    fun isTooOld(state: DefinitionState): Boolean =
        state is DefinitionState.Unavailable && state.reason == DefinitionUnavailableReason.KatachiTooOld
}
