package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate

/** The definition modules are katachi 0.2.x: [katachiVersion] is the version they report, `null` when unknown. */
internal data class DefinitionTooOld(val katachiVersion: String?)

/**
 * Whether every definition module is katachi 0.2.x (design T-8): it has the templates task only, so templates work but the
 * gutter, the violation underline and the "no role matches" notice cannot. A snapshot's module (the tasks that answered)
 * counts over the detected one, whose task list may still be unknown. `null` when some module reads the definition.
 *
 * ```kotlin
 * val tooOld = definitionTooOldOf(state.modules, state.snapshots) ?: return   // no band
 * ```
 */
internal fun definitionTooOldOf(modules: List<KatachiModule>, snapshots: List<DescriptionSnapshot>): DefinitionTooOld? {
    val answered = modules.map { module -> snapshots.firstOrNull { it.module.id == module.id }?.module ?: module }
    if (answered.isEmpty() || answered.any { it.readsDefinition } || snapshots.any { it.definition != null }) return null
    return DefinitionTooOld(snapshots.firstNotNullOfOrNull { it.katachiVersion })
}

/**
 * Whether the row's menu offers "Open Definition" (design T-10): the row's module was read with a definition model. Without
 * one (katachi 0.2.x, nothing read yet) there is no declaration to open. A template set's row has no Role and its
 * declaration is not in the JSON, so it never offers it.
 */
internal fun canOpenDefinition(row: ModuleTemplate, snapshots: List<DescriptionSnapshot>): Boolean =
    row.template.set == null && snapshots.any { it.module.id == row.module.id && it.definition != null }

/** Where the Role of [row] was declared; `null` when the definition does not know the Role or did not capture where. */
internal fun roleDeclarationOf(row: ModuleTemplate, snapshots: List<DescriptionSnapshot>): DeclarationSiteRef? =
    snapshots.firstOrNull { it.module.id == row.module.id }?.definition?.roles
        ?.firstOrNull { it.qualifiedName == row.template.roleName }?.declaredAt
