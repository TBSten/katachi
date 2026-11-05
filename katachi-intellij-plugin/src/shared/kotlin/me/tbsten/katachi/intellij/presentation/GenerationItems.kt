package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.generate.GenerationItem
import me.tbsten.katachi.intellij.data.generate.TemplateArgsEntry
import me.tbsten.katachi.intellij.data.generate.templateArgsOf
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ModuleTemplate

internal fun generationItemsOf(rows: List<ModuleTemplate>, form: FormState): List<GenerationItem> {
    val groups = mutableListOf<MutableList<ModuleTemplate>>()
    val plainGroupOf = mutableMapOf<ModuleId, MutableList<ModuleTemplate>>()
    for (row in rows) {
        if (row.template.detail == null) continue
        if (row.template.set != null) {
            groups += mutableListOf(row)
        } else {
            plainGroupOf.getOrPut(row.module.id) { mutableListOf<ModuleTemplate>().also { groups += it } } += row
        }
    }
    return groups.map { itemOf(it, form) }
}

private fun itemOf(group: List<ModuleTemplate>, form: FormState): GenerationItem {
    val checked = group.mapNotNull { row -> row.template.detail?.let { row to it } }
    val entries = checked.map { (row, detail) -> TemplateArgsEntry(row.template.template, detail, form.inputsOf(row.id)) }
    val expected = checked.flatMap { (row, detail) ->
        expectedFilesOf(detail, form.inputsOf(row.id)).mapNotNull { (it.location as? ExpectedLocation.Known)?.path }
            .mapNotNull { resolveExpectedPath(row.module.linkedRootPath, it) }
    }
    return GenerationItem(
        templateIds = checked.map { (row, _) -> row.id },
        module = checked.first().first.module,
        args = templateArgsOf(entries, form.onExisting),
        expectedPaths = expected,
    )
}
