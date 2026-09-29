package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin

/**
 * The capture values a place decides, for any template: the dialog asks again when the user picks
 * another template (decision 4). The real one is `TemplatePlacementIndex.seedsFor` of the current
 * index (A1); D1 tests with a fake.
 *
 * ```kotlin
 * val seeds = CaptureSeedPort { origin, template -> index.seedsFor(origin, template) }
 * ```
 */
internal fun interface CaptureSeedPort {
    /** [template]'s captures [origin] decides, capture name to value; empty when it does not fit there. */
    fun seedsFor(origin: EntryOrigin, template: TemplateId): Map<String, String>
}
