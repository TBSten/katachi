package me.tbsten.katachi.intellij.ide.newmenu

import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.NewMenuNode
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import me.tbsten.katachi.intellij.presentation.entry.newMenuTreeOf
import java.nio.file.Path

/**
 * What New › katachi shows for the selected directories: the tree, and for each template the
 * directory the dialog starts from.
 *
 * Several directories give the union of what fits each; a template that fits more than one is listed
 * once, and its origin and initial captures are those of the first directory it fits (decision 23).
 * Pure over the index state, so it neither touches the SDK nor runs Gradle (issue 4).
 */
internal class NewMenuResolution(
    val tree: List<NewMenuNode>,
    private val origins: Map<TemplateId, Path>,
) {
    /** The directory the dialog for [match] starts from. */
    fun originOf(match: PlacementMatch): Path? = origins[match.id]

    /** Whether the katachi group has anything to show. */
    val isEmpty: Boolean get() = tree.isEmpty()

    companion object {
        val EMPTY: NewMenuResolution = NewMenuResolution(emptyList(), emptyMap())

        /** No directory: nothing to show, whatever the state. */
        fun of(state: PlacementIndexState, directories: List<Path>): NewMenuResolution {
            if (directories.isEmpty()) return EMPTY
            val matches = LinkedHashMap<TemplateId, PlacementMatch>()
            val origins = LinkedHashMap<TemplateId, Path>()
            if (state is PlacementIndexState.Ready) {
                for (directory in directories) {
                    for (match in state.index.matchesForDirectory(directory)) {
                        if (matches.putIfAbsent(match.id, match) == null) origins[match.id] = directory
                    }
                }
            }
            return NewMenuResolution(newMenuTreeOf(state.availability, matches.values.toList()), origins)
        }
    }
}
