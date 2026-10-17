package me.tbsten.katachi.intellij.model

import java.time.Instant

/** When in a run the definition was found wrong, by the `phase` of `definition-errors.json`. */
internal enum class DefinitionErrorPhase {
    /** `architecture { }` threw while the generated entry point was being initialized. */
    EntryPoint,

    /** A processor threw or failed while it ran. */
    Processor,
}

/**
 * One mistake of the definition, as `definition-errors.json` reports it.
 *
 * [site] is where the mistake was written, `null` when katachi could not tell; such a mistake is
 * never put on a line and is only shown in a notification.
 *
 * ```kotlin
 * val first = errors.errors.firstOrNull() ?: return
 * val line = first.site?.line
 * ```
 */
internal data class DefinitionErrorModel(
    val module: ModuleId,
    val phase: DefinitionErrorPhase,
    /** The key of the processor that was running, for [DefinitionErrorPhase.Processor]. */
    val processor: String?,
    /** The class name of the exception. */
    val exceptionClassName: String,
    /** The whole message, line breaks included. */
    val message: String,
    val site: DeclarationSiteRef?,
)

/**
 * The mistakes of the definition the last finished load found (design 2.3). [errors] is empty when
 * there is none. [stale] says the definition's files changed after they were read.
 */
internal data class DefinitionErrors(
    val errors: List<DefinitionErrorModel> = emptyList(),
    /** When the load that found [errors] ended; `null` when no mistake was ever read. */
    val readAt: Instant? = null,
    val stale: Boolean = false,
) {
    companion object {
        /** No mistake, nothing read. */
        val None: DefinitionErrors = DefinitionErrors()
    }
}
