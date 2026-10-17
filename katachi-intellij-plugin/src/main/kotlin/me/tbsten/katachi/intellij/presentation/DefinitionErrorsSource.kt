package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import java.time.Instant

/**
 * What happened to the definition's mistakes when the tool window's state went from [previous] to
 * [source] at [now] (design 2.3), or `null` when nothing did. Derived from the same source as the
 * definition state, so the two never disagree about which load is the latest.
 *
 * - A load starting is [DefinitionErrorsEvent.LoadStarted].
 * - A load ending is [DefinitionErrorsEvent.LoadRejected] when katachi found the definition wrong,
 *   [DefinitionErrorsEvent.Cancelled], [DefinitionErrorsEvent.LoadFailedOther] for any other failure, and
 *   [DefinitionErrorsEvent.LoadOk] when the load replaced the list on screen (one that left it as it was is a cancel).
 * - A load that ends with a failure the screen already showed before it started is a cancel: the ViewModel keeps the
 *   banner of an earlier load while a new one runs, and a cancel leaves it, so only a failure that is a new object
 *   (`applyLoadFailed` makes one every time) counts as this load's.
 * - A load that ends without the loading state having been seen (the two states of the ViewModel were merged into
 *   one) still counts, once: a rejection is [DefinitionErrorsEvent.LoadRejected], and the list replaced after a failure
 *   was on screen is [DefinitionErrorsEvent.LoadOk].
 *
 * ```kotlin
 * val event = definitionErrorsEventOf(lastSource, source, Instant.now()) ?: return
 * errors = errors.after(event)
 * ```
 */
internal fun definitionErrorsEventOf(
    previous: DefinitionStateSource?,
    source: DefinitionStateSource,
    now: Instant,
): DefinitionErrorsEvent? {
    val wasLoading = previous?.loading == true
    if (source.loading) return if (wasLoading) null else DefinitionErrorsEvent.LoadStarted
    val rejected = (source.failure as? LoadFailure.Gradle)?.failure as? GradleFailure.DefinitionRejected
    if (wasLoading) return endOf(previous, source, rejected, now)
    if (rejected != null && previous?.failure != source.failure) return DefinitionErrorsEvent.LoadRejected(rejected.errors, now)
    // A load that read the definition between two states the observer saw, with the loading one missed.
    if (previous?.failure != null && source.failure == null && source.loadStarted && previous.snapshots !== source.snapshots) {
        return DefinitionErrorsEvent.LoadOk(now)
    }
    return null
}

private fun endOf(
    previous: DefinitionStateSource?,
    source: DefinitionStateSource,
    rejected: GradleFailure.DefinitionRejected?,
    now: Instant,
): DefinitionErrorsEvent = when {
    rejected != null && source.failure !== previous?.failure -> DefinitionErrorsEvent.LoadRejected(rejected.errors, now)
    rejected != null -> DefinitionErrorsEvent.Cancelled
    source.failure == LoadFailure.Cancelled -> DefinitionErrorsEvent.Cancelled
    source.failure != null -> DefinitionErrorsEvent.LoadFailedOther
    // A load that read the definition replaced the list; a cancelled one leaves the list it found.
    source.loadStarted && previous?.snapshots !== source.snapshots -> DefinitionErrorsEvent.LoadOk(now)
    else -> DefinitionErrorsEvent.Cancelled
}
