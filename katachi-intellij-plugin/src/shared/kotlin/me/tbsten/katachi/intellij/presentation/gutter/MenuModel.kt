package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.TemplateId
import java.nio.file.Path

/** What a menu row does when it is chosen. The IDE side turns it into a call. */
internal sealed interface MenuTarget {
    /** Opens the line of a definition's file ([module] decides which definition's source roots to look in). */
    data class Site(val module: ModuleId, val site: DeclarationSiteRef) : MenuTarget

    /** Opens the file at [path] (absolute). */
    data class OpenFile(val path: Path) : MenuTarget

    /** Opens the generate dialog on [template], started from [file] (the file of the gutter, absolute). */
    data class Generate(val template: TemplateId, val file: Path) : MenuTarget

    /** The foundation's `katachi.RunCheck`. */
    data object RunCheck : MenuTarget

    /** The foundation's `katachi.RefreshDefinition`. */
    data object Refresh : MenuTarget
}

/** One thing in the menu. */
internal sealed interface MenuItem {
    /** A line to choose. [enabled] is `false` when it cannot be chosen now ([target] is `null` for a line that is only text). */
    data class Row(
        val text: String,
        val trailing: String? = null,
        val target: MenuTarget? = null,
        val enabled: Boolean = target != null,
    ) : MenuItem

    /** A heading over the rows below it. */
    data class Section(val title: String) : MenuItem

    /** A line that opens [rows]. */
    data class Submenu(val text: String, val rows: List<Row>) : MenuItem

    data object Separator : MenuItem
}

/** The menu of a ◆ (design 2.3): [title] over [items], in the order to show them. */
internal data class MenuModel(val title: String, val items: List<MenuItem>) {
    /** The definition sites the rows open: the IDE resolves them before the menu is shown. */
    val sites: Set<MenuTarget.Site>
        get() = rows().mapNotNull { it.target as? MenuTarget.Site }.toSet()

    /** Every row, the ones of sub menus included. */
    fun rows(): List<MenuItem.Row> = items.flatMap { item ->
        when (item) {
            is MenuItem.Row -> listOf(item)
            is MenuItem.Submenu -> item.rows
            else -> emptyList()
        }
    }

    companion object {
        /**
         * The menu for [input]. [availability] is read at the moment the ◆ is pressed (a check may be running now); the rest of
         * [input] was captured when the ◆ was drawn.
         *
         * ```kotlin
         * val menu = MenuModel.of(MenuInputs.of(mark, fileRoles), MenuAvailability(runCheck = true, refresh = true, checkRunning = false))
         * menu.items.first() // the line that links to what allowed the declaration
         * ```
         */
        fun of(input: MenuInput, availability: MenuAvailability): MenuModel = MenuModels.build(input, availability)
    }
}

/** What can be chosen right now, read when the menu is opened. */
internal data class MenuAvailability(
    /** The IDE has the `katachi.RunCheck` action. */
    val runCheck: Boolean,
    /** The IDE has the `katachi.RefreshDefinition` action. */
    val refresh: Boolean,
    /** The module's check is running ([`ViolationsService.runningChecks`]). */
    val checkRunning: Boolean,
)

/** One template [Role]'s menu offers: the row's text is [label] (the title, else the specifier). */
internal data class TemplateChoice(val id: TemplateId, val label: String)

/** One role's part of a menu: everything the rows need, captured when the ◆ was drawn. */
internal data class RoleMenuInput(
    val role: RoleModel,
    /** What allowed the declaration under this role; `null` for a ◆ of the file. */
    val explanation: BoundExplanation?,
    /** Where this role's `ktFileContent {` was written. */
    val constraintSite: DeclarationSiteRef?,
    /** The required lines no declaration of the file matched. */
    val missing: List<RuleRef>,
    val siblings: SiblingFiles,
    /** The directory [siblings] are relative to. */
    val projectRoot: Path,
    val templates: List<TemplateChoice>,
)

/** What [MenuModel.of] reads of one ◆. */
internal data class MenuInput(
    val title: String,
    /** The file the ◆ is in, absolute. */
    val file: Path,
    val module: KatachiModule,
    /** The explanations may be older than the file or the definition (the ◆'s `(stale)`). */
    val stale: Boolean,
    val roles: List<RoleMenuInput>,
    /** The definition sites that could not be found, with the reason: their rows cannot be chosen. */
    val unresolved: Map<DeclarationSiteRef, String> = emptyMap(),
)

internal object MenuModels {
    fun build(input: MenuInput, availability: MenuAvailability): MenuModel {
        val items = mutableListOf<MenuItem>()
        val needsRefreshToSee = input.roles.any { it.explanation == BoundExplanation.NotComputed }
        val refresh = refreshRow(availability)
        if (input.stale && !needsRefreshToSee) items += refresh
        val single = input.roles.singleOrNull()
        val newInSection = single?.templates?.isNotEmpty() == true
        for (role in input.roles) {
            if (single == null) items += MenuItem.Section(role.role.qualifiedName)
            items += roleItems(input, role, availability, separateNew = newInSection)
        }
        if (!newInSection) items += MenuItem.Separator
        items += runCheckRow(input, availability)
        if (!(input.stale && !needsRefreshToSee)) items += refresh
        return MenuModel(input.title, items)
    }

    private fun roleItems(input: MenuInput, role: RoleMenuInput, availability: MenuAvailability, separateNew: Boolean): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        explanationRow(input, role, availability)?.let { items += it }
        if (role.missing.isNotEmpty()) {
            items += MenuItem.Section("Missing in this file (${role.missing.size})")
            items += role.missing.map { rule -> siteRow(input, rule.text, rule.declaredAt) }
            // Without it the Role row reads as one more missing rule.
            items += MenuItem.Separator
        }
        items += roleRow(input, role)
        items += siblingItems(role, input.stale)
        newItem(input, role)?.let {
            if (separateNew) items += MenuItem.Separator
            items += it
        }
        return items
    }

    private fun explanationRow(input: MenuInput, role: RoleMenuInput, availability: MenuAvailability): MenuItem.Row? {
        val explanation = role.explanation ?: return null
        val declaration = when (explanation) {
            BoundExplanation.NotComputed -> return MenuItem.Row(
                "⟳ Reload Definition to see what allows this",
                target = MenuTarget.Refresh,
                enabled = availability.refresh,
            )
            is BoundExplanation.Explained -> explanation.declaration
        }
        val first = declaration.rules.firstOrNull()
        val more = if (declaration.rules.size > 1) " +${declaration.rules.size - 1}" else ""
        val ruleText = (first?.text ?: "a line katachi did not record") + more
        return when (val verdict = declaration.verdict) {
            DeclarationVerdict.Allowed, DeclarationVerdict.AllowedByAnythingElse ->
                siteRow(input, "◆ Allowed by $ruleText", first?.declaredAt)
            DeclarationVerdict.Prohibited -> siteRow(input, "◆ Prohibited by $ruleText", first?.declaredAt)
            DeclarationVerdict.Mismatched ->
                siteRow(input, "◆ Does not match $ruleText" + declaration.difference?.let { ": $it" }.orEmpty(), first?.declaredAt)
            DeclarationVerdict.Unexpected -> siteRow(input, "◆ Not allowed — see ktFileContent", role.constraintSite)
            is DeclarationVerdict.Unknown -> MenuItem.Row("◆ Judged ${verdict.jsonName} by a newer katachi")
        }
    }

    /** A row that opens [site] of the definition; it cannot be chosen when the site was not recorded or was not found. */
    private fun siteRow(input: MenuInput, text: String, site: DeclarationSiteRef?): MenuItem.Row {
        if (site == null) return MenuItem.Row(text)
        val reason = input.unresolved[site]
        return if (reason == null) {
            MenuItem.Row(text, site.shown(), MenuTarget.Site(input.module.id, site))
        } else {
            MenuItem.Row(text, "(declaration not found: $reason)", MenuTarget.Site(input.module.id, site), enabled = false)
        }
    }

    private fun roleRow(input: MenuInput, role: RoleMenuInput): MenuItem.Row {
        val what = (role.role.title ?: role.role.summary)?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
        val text = "◇ Role ${role.role.qualifiedName}" + what?.let { " — $it" }.orEmpty()
        return siteRow(input, text, role.role.declaredAt)
    }

    /** The files the definition listed. A file made since is not among them, which the heading says while the answer is [stale]. */
    private fun siblingItems(role: RoleMenuInput, stale: Boolean): List<MenuItem> {
        val siblings = role.siblings
        val name = role.role.qualifiedName
        val note = if (stale) ", stale" else ""
        if (siblings.files.isEmpty()) return listOf(MenuItem.Section("Other files of $name" + if (stale) " (stale)" else ""), MenuItem.Row("— No other file yet"))
        val rows = siblings.files.map { path ->
            MenuItem.Row(SiblingFiles.nameOf(path), SiblingFiles.shortDirectory(SiblingFiles.parentOf(path)), MenuTarget.OpenFile(role.projectRoot.resolve(path)))
        }
        val more = if (siblings.more > 0) listOf(MenuItem.Row("… and ${siblings.more} more")) else emptyList()
        return listOf(MenuItem.Section("Other files of $name (${siblings.files.size + siblings.more}$note)")) + rows + more
    }

    private fun newItem(input: MenuInput, role: RoleMenuInput): MenuItem? {
        val text = "+ New ${role.role.qualifiedName} from template…"
        val rows = role.templates.map { MenuItem.Row(it.label, target = MenuTarget.Generate(it.id, input.file)) }
        return when (rows.size) {
            0 -> null
            1 -> rows.single().copy(text = text)
            else -> MenuItem.Submenu(text, rows)
        }
    }

    private fun runCheckRow(input: MenuInput, availability: MenuAvailability): MenuItem.Row {
        if (availability.checkRunning) return MenuItem.Row("▶ Checking…", enabled = false, target = MenuTarget.RunCheck)
        return MenuItem.Row(
            "▶ Run the check (${input.module.taskPath("test")})",
            target = MenuTarget.RunCheck,
            enabled = availability.runCheck,
        )
    }

    private fun refreshRow(availability: MenuAvailability): MenuItem.Row =
        MenuItem.Row("⟳ Reload Definition", target = MenuTarget.Refresh, enabled = availability.refresh)

    private fun DeclarationSiteRef.shown(): String = "$fileName:$line"
}
