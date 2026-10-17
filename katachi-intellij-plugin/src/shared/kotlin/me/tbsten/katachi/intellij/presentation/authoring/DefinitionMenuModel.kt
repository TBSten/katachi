package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.TemplateId
import java.nio.file.Path

/** What choosing a row of the definition ◆'s menu does; the IDE side turns it into a call. */
internal sealed interface DefinitionMenuTarget {
    /** Opens the file at [path] (absolute), a file the role holds. */
    data class OpenFile(val path: Path) : DefinitionMenuTarget

    /** Opens the generate dialog on [template], started from the definition file ([file], absolute). */
    data class Generate(val template: TemplateId, val file: Path) : DefinitionMenuTarget

    /** Shows [template] in the tool window. */
    data class Reveal(val template: TemplateId) : DefinitionMenuTarget

    /** The foundation's `katachi.RunCheck`. */
    data object RunCheck : DefinitionMenuTarget

    /** The foundation's `katachi.RefreshDefinition`, named "Reload Definition". */
    data object Refresh : DefinitionMenuTarget
}

/** One thing in the menu. */
internal sealed interface DefinitionMenuItem {
    /** A line to choose; a line without a [target] is only text. */
    data class Row(
        val text: String,
        val trailing: String? = null,
        val target: DefinitionMenuTarget? = null,
        val enabled: Boolean = target != null,
    ) : DefinitionMenuItem

    /** A heading over the rows below it. */
    data class Section(val title: String) : DefinitionMenuItem

    /** A line that opens [rows]. */
    data class Submenu(val text: String, val rows: List<Row>) : DefinitionMenuItem

    data object Separator : DefinitionMenuItem
}

/** The menu of a definition ◆ (ide-authoring design 4.3): [title] over [items], in the order to show them. */
internal data class DefinitionMenuModel(val title: String, val items: List<DefinitionMenuItem>) {
    /** Every row, the ones of sub menus included. */
    fun rows(): List<DefinitionMenuItem.Row> = items.flatMap { item ->
        when (item) {
            is DefinitionMenuItem.Row -> listOf(item)
            is DefinitionMenuItem.Submenu -> item.rows
            else -> emptyList()
        }
    }
}

/** What can be chosen right now, read when the menu opens. */
internal data class DefinitionMenuAvailability(val runCheck: Boolean, val refresh: Boolean)
