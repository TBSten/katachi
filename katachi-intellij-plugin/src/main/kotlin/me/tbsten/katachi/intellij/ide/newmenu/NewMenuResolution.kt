package me.tbsten.katachi.intellij.ide.newmenu

import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.NewMenuNode
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import me.tbsten.katachi.intellij.presentation.entry.newMenuTreeOf
import java.nio.file.Path

internal class NewMenuResolution(
    val tree: List<NewMenuNode>,
    private val origins: Map<TemplateId, Path>,
) {
    fun originOf(match: PlacementMatch): Path? = origins[match.id]

    val isEmpty: Boolean get() = tree.isEmpty()

    companion object {
        val EMPTY: NewMenuResolution = NewMenuResolution(emptyList(), emptyMap())

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
