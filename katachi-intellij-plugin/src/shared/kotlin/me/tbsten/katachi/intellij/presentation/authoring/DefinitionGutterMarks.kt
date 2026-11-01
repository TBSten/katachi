package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.DeclarationKind
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.TemplateRef
import me.tbsten.katachi.intellij.presentation.gutter.RoleFileCount
import java.nio.file.Path

/** The two lines of a definition file that get a ◆ (ide-authoring D-14): a role, and a `.template { }`. */
internal enum class DefinitionGutterMarkKind { Role, Template }

/** A template a menu offers to generate from: the row says [label] (the title, else the specifier). */
internal data class DefinitionTemplateChoice(val id: TemplateId, val label: String)

/** One role's part of a ◆: everything its menu needs, captured when the ◆ is drawn. */
internal data class DefinitionRoleInput(
    val name: String,
    /** Distinct files the role holds, relative to the project root, in the order the walk listed them (capped by the JSON). */
    val files: List<String>,
    /** How many files the role holds, which is more than [files] when the JSON cut the list; `null` when the walk could not run. */
    val count: Int?,
    val templates: List<DefinitionTemplateChoice>,
)

/** What a ◆ shows and opens. */
internal data class DefinitionGutterMark(
    val kind: DefinitionGutterMarkKind,
    val tooltip: String,
    val module: ModuleId,
    /** The definition file the ◆ is in, absolute. */
    val definitionFile: Path,
    /** The directory the role's files are relative to. */
    val projectRoot: Path,
    val roles: List<DefinitionRoleInput>,
    /** The answer may be older than the source (design 4.4): the menu starts with the reload. */
    val stale: Boolean,
) {
    /** The menu as it is now. */
    fun menu(availability: DefinitionMenuAvailability, strings: AuthoringStrings): DefinitionMenuModel = DefinitionMenus.build(this, availability, strings)
}

/**
 * The ◆ a bound anchor gets, or `null` for an anchor that has none (an entry, or one nothing in the model matches).
 *
 * ```kotlin
 * val mark = DefinitionGutterMarks.of(bound, definitionFile, projectRoot, stale = false, strings)
 * ```
 */
internal object DefinitionGutterMarks {
    fun of(bound: BoundAnchor, definitionFile: Path, projectRoot: Path, stale: Boolean, strings: AuthoringStrings): DefinitionGutterMark? {
        val kind = when (bound.anchor.kind) {
            AnchorKind.Role -> DefinitionGutterMarkKind.Role
            AnchorKind.Template -> DefinitionGutterMarkKind.Template
            AnchorKind.Entry -> return null
        }
        val declarations = bound.declarations.filter { it.role != null }
        val module = declarations.firstOrNull()?.module ?: return null
        val roles = declarations.mapNotNull { it.role }.distinctBy { it.qualifiedName }.map { roleInput(it, module) }
        val shown = when (kind) {
            // A `.template { }` ◆ generates from the templates of that line only, whatever else its role offers.
            DefinitionGutterMarkKind.Template -> roles.map { role ->
                val own = declarations.filter { it.kind == DeclarationKind.Template && it.role?.qualifiedName == role.name }
                    .mapNotNull { it.entry?.template }.distinctBy { it.specifier }.map { choiceOf(module, it) }
                role.copy(templates = own)
            }
            DefinitionGutterMarkKind.Role -> roles
        }
        val tooltip = when (kind) {
            DefinitionGutterMarkKind.Role -> roleTooltip(shown, stale, strings)
            DefinitionGutterMarkKind.Template -> "katachi: " + strings.generateFrom(shown.joinToString(", ") { it.name }) + staleNote(stale, strings)
        }
        return DefinitionGutterMark(kind, tooltip, module, definitionFile, projectRoot, shown, stale)
    }

    private fun roleInput(role: RoleModel, module: ModuleId): DefinitionRoleInput {
        val files = RoleFileCount.holders(role.layout).flatMap { it.matchedFiles }.distinct()
        val count = RoleFileCount.of(role.layout)
        val templates = role.layout.mapNotNull { it.template }.distinctBy { it.specifier }.map { choiceOf(module, it) }
        return DefinitionRoleInput(role.qualifiedName, files, count, templates)
    }

    private fun choiceOf(module: ModuleId, template: TemplateRef) = DefinitionTemplateChoice(TemplateId(module, template.specifier), template.title ?: template.specifier)

    private fun roleTooltip(roles: List<DefinitionRoleInput>, stale: Boolean, strings: AuthoringStrings): String {
        val names = roles.joinToString(", ") { it.name }
        val counts = roles.map { it.count }
        return "katachi: $names — ${strings.files(if (counts.any { it == null }) null else counts.sumOf { it ?: 0 })}" + staleNote(stale, strings)
    }

    private fun staleNote(stale: Boolean, strings: AuthoringStrings): String = if (stale) strings.staleSuffix else ""
}
