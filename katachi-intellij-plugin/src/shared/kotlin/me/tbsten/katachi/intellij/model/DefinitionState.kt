package me.tbsten.katachi.intellij.model

import java.time.Instant

/**
 * What one definition module's JSON said, and when that JSON was written. [definition] is `null`
 * for a module that only has the templates task (katachi 0.2.x), whose [templates] are still there.
 */
internal data class DefinitionSnapshot(
    val module: KatachiModule,
    val definition: DefinitionModel?,
    val templates: List<TemplateModel>,
    /** From the synced libraries, or the JSON's own; `null` when unknown. */
    val katachiVersion: String?,
    /** The JSON file's modification time. */
    val loadedAt: Instant,
)

/**
 * The definition model as `DefinitionModelService.state` publishes it (design 4.3). An answer is
 * never taken away while a newer one is on its way: [Loading], [Unavailable] and a [Freshness.Stale]
 * [Ready] all keep the last [Ready]'s definitions, so the two features keep drawing.
 *
 * ```kotlin
 * val ready = service.state.value.answer ?: return          // Ready, or the one a newer state still carries
 * val stale = ready.freshness is Freshness.Stale
 * ```
 */
internal sealed interface DefinitionState {
    /** A definition module exists but nothing was read: the user of the service calls `ensureLoaded()`. */
    data object NotLoaded : DefinitionState

    /** Gradle is running. [previous] is still shown. */
    data class Loading(val previous: Ready?) : DefinitionState

    data class Ready(val definitions: List<DefinitionSnapshot>, val freshness: Freshness) : DefinitionState

    data class Unavailable(val reason: DefinitionUnavailableReason, val previous: Ready?) : DefinitionState
}

internal sealed interface Freshness {
    data object Fresh : Freshness

    /** The definition changed (or a reload failed) since [since]; the answer is the one from before. */
    data class Stale(val since: Instant, val reason: StaleReason) : Freshness
}

internal sealed interface StaleReason {
    /** A source or build script of the definition module changed. */
    data object DefinitionChanged : StaleReason

    data class ReloadFailed(val failure: LoadFailure) : StaleReason
}

internal enum class DefinitionUnavailableReason { NotSynced, NoDefinition, KatachiTooOld, LoadFailed, LoadingWithoutUserDisabled }

/** The [DefinitionState.Ready] to answer from: the state itself, or the one it still carries. `null` when nothing was ever read. */
internal val DefinitionState.answer: DefinitionState.Ready?
    get() = when (this) {
        DefinitionState.NotLoaded -> null
        is DefinitionState.Loading -> previous
        is DefinitionState.Ready -> this
        is DefinitionState.Unavailable -> previous
    }

/** Gradle started: [DefinitionState.Loading], keeping the answer. Already loading is the same instance. */
internal fun DefinitionState.startLoading(): DefinitionState = when (this) {
    is DefinitionState.Loading -> this
    else -> DefinitionState.Loading(answer)
}

/**
 * The definition changed at [at] (or a reload failed, [reason]): the answer stays, marked
 * [Freshness.Stale]. Marking again keeps the first `since`, and the same reason gives the same instance
 * (so a state flow does not emit twice). Nothing to mark in [DefinitionState.NotLoaded] and
 * [DefinitionState.Unavailable].
 */
internal fun DefinitionState.markStale(at: Instant, reason: StaleReason): DefinitionState = when (this) {
    DefinitionState.NotLoaded, is DefinitionState.Unavailable -> this
    is DefinitionState.Loading -> {
        val staled = previous?.staled(at, reason)
        if (staled == null || staled === previous) this else copy(previous = staled)
    }
    is DefinitionState.Ready -> staled(at, reason)
}

private fun DefinitionState.Ready.staled(at: Instant, reason: StaleReason): DefinitionState.Ready {
    val current = freshness
    if (current is Freshness.Stale && current.reason == reason) return this
    val since = (current as? Freshness.Stale)?.since ?: at
    return copy(freshness = Freshness.Stale(since, reason))
}

/**
 * A read finished: [DefinitionState.Ready] and [Freshness.Fresh]. When the previous answer already
 * held the same [definitions] the list instance is reused, so unchanged content looks unchanged.
 */
internal fun DefinitionState.loaded(definitions: List<DefinitionSnapshot>): DefinitionState.Ready {
    val kept = answer?.definitions?.takeIf { it == definitions } ?: definitions
    val fresh = DefinitionState.Ready(kept, Freshness.Fresh)
    return (this as? DefinitionState.Ready)?.takeIf { it == fresh } ?: fresh
}

/**
 * A read failed at [at]. With an answer to keep, it stays and becomes [Freshness.Stale] with
 * [StaleReason.ReloadFailed]; with none, the module is [DefinitionState.Unavailable] for [DefinitionUnavailableReason.LoadFailed].
 */
internal fun DefinitionState.loadFailed(at: Instant, failure: LoadFailure): DefinitionState {
    val kept = answer ?: return DefinitionState.Unavailable(DefinitionUnavailableReason.LoadFailed, previous = null)
    return kept.staled(at, StaleReason.ReloadFailed(failure))
}

/** The module has nothing to read ([reason]); a previous answer, if any, stays. */
internal fun DefinitionState.unavailable(reason: DefinitionUnavailableReason): DefinitionState =
    DefinitionState.Unavailable(reason, answer)
