package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import java.time.Instant

// A failure already on screen before the load started counts as a cancel: only a new failure object (`applyLoadFailed` makes one every time) is this load's.
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
