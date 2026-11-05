package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.TemplateRef

/**
 * Builders of what a definition says about where it was written, for the definition side of the gutter. Platform-free: compiled into
 * `uiTest` too.
 *
 * ```kotlin
 * val entry = layoutEntry(pattern).declaredAt("ServiceRole.kt", 3).withTemplate("domain.Service.service", "ServiceRole.kt", 4)
 * val role = roleModel("domain.Service", entry).declaredAt("ServiceRole.kt", 2)
 * ```
 */
internal fun LayoutEntryModel.declaredAt(fileName: String, line: Int): LayoutEntryModel = copy(declaredAt = DeclarationSiteRef(fileName, line))

/** [this] with a `.template { }` written at [line] of [fileName]; [specifier] is the complete one. */
internal fun LayoutEntryModel.withTemplate(specifier: String, fileName: String, line: Int, title: String? = null): LayoutEntryModel =
    copy(template = TemplateRef(specifier, null, title, DeclarationSiteRef(fileName, line)))

internal fun RoleModel.declaredAt(fileName: String, line: Int): RoleModel = copy(declaredAt = DeclarationSiteRef(fileName, line))
