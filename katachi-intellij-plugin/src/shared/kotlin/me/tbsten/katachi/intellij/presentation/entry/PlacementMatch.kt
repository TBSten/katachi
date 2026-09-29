package me.tbsten.katachi.intellij.presentation.entry

import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId

/**
 * One template that fits a file (the editor notification) or a directory (the New menu), as the
 * placement index (`data/placement`) finds it.
 *
 * It lives here, not beside the index, because the New menu tree and the notification decision of
 * this package read it, and `src/shared` cannot see `data/`.
 *
 * The rules (spike S1, `.local/idea-plugin/impl/spike-placement.md`): a pattern is matched segment by
 * segment against the path relative to its definition's project root. Captures (`${x}`) of the
 * segments above the place are [decided] (initial values the dialog shows, still editable: issue 1);
 * those of the segments at or below it are [undecided] (empty required fields). A module-derived
 * `<x>` is never a field; when one stays below the place, the IDE cannot know the target path before
 * katachi runs ([targetUndecided]).
 *
 * ```kotlin
 * val match = PlacementMatch(ModuleTemplate(module, repository), decided = mapOf("feature" to "home"), undecided = listOf("name"),
 *     remainingPath = "component/<feature><name>.kt", targetUndecided = true)
 * ```
 */
internal data class PlacementMatch(
    /** The template, with the architecture definition (definition module) it belongs to. */
    val template: ModuleTemplate,
    /** Captures the place decides, in pattern order: capture name to value. */
    val decided: Map<String, String>,
    /** Captures the place leaves open, in pattern order. Empty for a file. */
    val undecided: List<String>,
    /**
     * The pattern below the place, `/` separated, captures written `<name>` (the user's notation):
     * the New menu item's label. Empty for a file.
     */
    val remainingPath: String,
    /** Whether a module-derived `<x>` remains in [remainingPath] (spike S1 §5). Always `false` for a file. */
    val targetUndecided: Boolean,
) {
    val id: TemplateId get() = template.id

    /** The architecture definition this match came from (issue 7). */
    val definition: KatachiModule get() = template.module
}

/**
 * Whether the New menu and the editor notification can use the template list right now (issues 3, 11, 18).
 * Derived from the project service's placement index state; pure, so the decisions can be tested as tables.
 */
internal enum class EntryAvailability {
    /** Found a definition module but nothing loaded yet: the New menu shows its one "loading" item and asks to load. */
    NotLoaded,

    /** A load is running and no index exists yet. */
    Loading,

    /** An index exists (possibly while a reload runs). */
    Ready,

    /** Not synced, katachi not installed, the JSON failed, or loading without the user is turned off and no cache exists: show nothing. */
    Unavailable,
}
