package me.tbsten.katachi.intellij.presentation.authoring

/** How many of a role's files the menu lists before it sends the user to the tool window. */
internal const val MENU_FILE_LIMIT: Int = 200

/**
 * The menus of the definition ◆ (ide-authoring design 4.3). English only, like the source file's gutter.
 *
 * A role: the files it holds (the first [MENU_FILE_LIMIT], then "… and N more"), then generating from its templates (one row, or a
 * sub menu), running the check and reloading the definition. A `.template { }`: generating from it and showing it in the
 * tool window. A ◆ whose answer may be old starts with the reload.
 */
internal object DefinitionMenus {
    fun build(mark: DefinitionGutterMark, availability: DefinitionMenuAvailability): DefinitionMenuModel = when (mark.kind) {
        DefinitionGutterMarkKind.Role -> roleMenu(mark, availability)
        DefinitionGutterMarkKind.Template -> templateMenu(mark)
    }

    private fun roleMenu(mark: DefinitionGutterMark, availability: DefinitionMenuAvailability): DefinitionMenuModel {
        val items = mutableListOf<DefinitionMenuItem>()
        val refresh = refreshRow(availability)
        if (mark.stale) items += refresh
        val several = mark.roles.size > 1
        for (role in mark.roles) {
            if (several) items += DefinitionMenuItem.Section(role.name)
            items += fileItems(mark, role)
            generateItem(mark, role)?.let { items += it }
        }
        items += DefinitionMenuItem.Separator
        items += runCheckRow(mark, availability)
        if (!mark.stale) items += refresh
        return DefinitionMenuModel(title(mark), items)
    }

    private fun templateMenu(mark: DefinitionGutterMark): DefinitionMenuModel {
        val several = mark.roles.size > 1
        // Several roles can be tied to one `.template { }` line (a helper function called for two groups): each template is a choice.
        val choices = mark.roles.flatMap { role -> role.templates.map { (if (several) "${role.name}: ${it.label}" else it.label) to it.id } }
            .distinctBy { it.second }
        val items = when (choices.size) {
            0 -> emptyList()
            1 -> listOf(
                DefinitionMenuItem.Row(GENERATE, target = DefinitionMenuTarget.Generate(choices.single().second, mark.definitionFile)),
                DefinitionMenuItem.Row(REVEAL, target = DefinitionMenuTarget.Reveal(choices.single().second)),
            )
            else -> listOf(
                DefinitionMenuItem.Submenu(GENERATE, choices.map { (label, id) -> DefinitionMenuItem.Row(label, target = DefinitionMenuTarget.Generate(id, mark.definitionFile)) }),
                DefinitionMenuItem.Submenu(REVEAL, choices.map { (label, id) -> DefinitionMenuItem.Row(label, target = DefinitionMenuTarget.Reveal(id)) }),
            )
        }
        return DefinitionMenuModel(title(mark), items)
    }

    private fun title(mark: DefinitionGutterMark): String = mark.tooltip.removePrefix("katachi: ")

    private fun fileItems(mark: DefinitionGutterMark, role: DefinitionRoleInput): List<DefinitionMenuItem> {
        if (role.files.isEmpty()) return listOf(DefinitionMenuItem.Row(if (role.count == null) "— File count unknown (the check has not run)" else "— No file yet"))
        val shown = role.files.take(MENU_FILE_LIMIT)
        val rows = shown.map { path ->
            DefinitionMenuItem.Row(path.substringAfterLast('/'), path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }, DefinitionMenuTarget.OpenFile(mark.projectRoot.resolve(path)))
        }
        val more = (role.count ?: shown.size) - shown.size
        return rows + if (more > 0) listOf(DefinitionMenuItem.Row("… and $more more (open the tool window)")) else emptyList()
    }

    private fun generateItem(mark: DefinitionGutterMark, role: DefinitionRoleInput): DefinitionMenuItem? {
        val rows = role.templates.map { DefinitionMenuItem.Row(it.label, target = DefinitionMenuTarget.Generate(it.id, mark.definitionFile)) }
        return when (rows.size) {
            0 -> null
            1 -> rows.single().copy(text = GENERATE)
            else -> DefinitionMenuItem.Submenu(GENERATE, rows)
        }
    }

    private fun runCheckRow(mark: DefinitionGutterMark, availability: DefinitionMenuAvailability) = DefinitionMenuItem.Row(
        "▶ Run the check (${taskPath(mark)})",
        target = DefinitionMenuTarget.RunCheck,
        enabled = availability.runCheck,
    )

    private fun refreshRow(availability: DefinitionMenuAvailability) =
        DefinitionMenuItem.Row("⟳ Reload Definition", target = DefinitionMenuTarget.Refresh, enabled = availability.refresh)

    private fun taskPath(mark: DefinitionGutterMark): String = mark.module.gradlePath.let { if (it == ":") ":test" else "$it:test" }

    private const val GENERATE = "+ Generate from this template…"
    private const val REVEAL = "⊞ Show in the tool window"
}
