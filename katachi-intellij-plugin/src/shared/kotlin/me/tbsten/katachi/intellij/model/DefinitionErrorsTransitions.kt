package me.tbsten.katachi.intellij.model

import java.time.Instant

/** What happened to the definition, as far as its mistakes are concerned (design 2.3). */
internal sealed interface DefinitionErrorsEvent {
    /** A load started. */
    data object LoadStarted : DefinitionErrorsEvent

    /** A load read the definition. */
    data class LoadOk(val at: Instant) : DefinitionErrorsEvent

    /** A load failed because katachi found the definition wrong. */
    data class LoadRejected(val errors: List<DefinitionErrorModel>, val at: Instant) : DefinitionErrorsEvent

    /** A load failed for another reason: it does not compile, Gradle failed, ... */
    data object LoadFailedOther : DefinitionErrorsEvent

    /** The definition module's files changed. */
    data object DefinitionChanged : DefinitionErrorsEvent

    /** A load was cancelled. */
    data object Cancelled : DefinitionErrorsEvent
}

/**
 * The mistakes after [event] (design 2.3). A mistake is read only from the last load that finished
 * and was rejected: another failure, a cancel and a start leave it as it is, a change of the
 * definition marks it stale without dropping it, and a load that reads the definition clears it.
 *
 * ```kotlin
 * val next = DefinitionErrors.None.after(DefinitionErrorsEvent.LoadRejected(errors, now))
 * ```
 */
internal fun DefinitionErrors.after(event: DefinitionErrorsEvent): DefinitionErrors = when (event) {
    DefinitionErrorsEvent.LoadStarted, DefinitionErrorsEvent.LoadFailedOther, DefinitionErrorsEvent.Cancelled -> this
    is DefinitionErrorsEvent.LoadOk -> if (errors.isEmpty() && !stale) this else DefinitionErrors.None
    is DefinitionErrorsEvent.LoadRejected -> DefinitionErrors(event.errors, event.at, stale = false)
    DefinitionErrorsEvent.DefinitionChanged -> if (errors.isEmpty()) this else copy(stale = true)
}
