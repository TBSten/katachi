package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.TemplateRef
import me.tbsten.katachi.intellij.presentation.gutter.GutterInput
import me.tbsten.katachi.intellij.presentation.gutter.GutterMark
import me.tbsten.katachi.intellij.presentation.gutter.GutterMarks
import me.tbsten.katachi.intellij.presentation.gutter.MenuAvailability
import me.tbsten.katachi.intellij.presentation.gutter.MenuInput
import me.tbsten.katachi.intellij.presentation.gutter.MenuInputs
import me.tbsten.katachi.intellij.presentation.gutter.MenuItem
import me.tbsten.katachi.intellij.presentation.gutter.MenuModel
import me.tbsten.katachi.intellij.presentation.gutter.SourceDeclaration
import me.tbsten.katachi.intellij.presentation.gutter.SourceFileKind

// Builders for the gutter menu's tests (ide-gutter P2). Platform-free: compiled into uiTest too.

/** A `.template { }` of [role]: the specifier is `<role>.<id>`. */
internal fun templateRef(role: RoleModel, id: String, title: String? = null): TemplateRef =
    TemplateRef("${role.qualifiedName}.$id", id, title, DeclarationSiteRef("${role.name}Role.kt", 30))

/** The role with [refs] on its layout entries in order; entries are added when it has fewer. */
internal fun RoleModel.withTemplates(vararg refs: TemplateRef): RoleModel {
    val entries = layout.toMutableList()
    refs.forEachIndexed { index, ref ->
        if (index < entries.size) entries[index] = entries[index].copy(template = ref) else entries += layoutEntry("extra$index.kt", index = index).copy(template = ref)
    }
    return copy(layout = entries)
}

/** The one ◆ (or the first) [GutterMarks] draws for a `.kt` file of [roles] with [declarations] and katachi's [explanations]. */
internal fun markOf(
    roles: FileRoles,
    declarations: List<SourceDeclaration>,
    explanations: List<RoleFileDeclarations> = emptyList(),
    stale: Boolean = false,
    index: Int = 0,
): GutterMark = GutterMarks.of(GutterInput(roles, explanations, declarations, SourceFileKind.Kotlin, stale))[index]

/** The menu input of [mark] with the definition sites in [unresolved] not found. */
internal fun menuInputOf(mark: GutterMark, roles: FileRoles, unresolved: Map<DeclarationSiteRef, String> = emptyMap()): MenuInput =
    MenuInputs.of(mark, roles).copy(unresolved = unresolved)

internal val ALL_ACTIONS: MenuAvailability = MenuAvailability(runCheck = true, refresh = true, checkRunning = false)

/** The rows of [model] as `text | trailing` lines, `-----` for a separator and `== title` for a heading: what a user would read. */
internal fun MenuModel.shown(): List<String> = items.flatMap { item ->
    when (item) {
        is MenuItem.Row -> listOf(item.shown())
        is MenuItem.Section -> listOf("== ${item.title}")
        is MenuItem.Submenu -> listOf("${item.text} >") + item.rows.map { "  " + it.shown() }
        MenuItem.Separator -> listOf("-----")
    }
}

internal fun MenuItem.Row.shown(): String = buildString {
    append(if (enabled) "" else "(disabled) ")
    append(text)
    trailing?.let { append(" | ").append(it) }
}
