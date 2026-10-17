package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.generate.GenerationItem
import me.tbsten.katachi.intellij.data.generate.TemplateArgsEntry
import me.tbsten.katachi.intellij.data.generate.templateArgsOf
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ModuleTemplate

/**
 * [rows] as the runs a generation makes, in list order:
 *
 * - the checked templates of one module share one [GenerationItem] (one `katachiTemplate` build, design draft section 6
 *   "IDE の複数選択"), placed where the module's first template stands;
 * - a template set is never combined: each checked set row is an item of its own, with `--arg template=<set>` and the
 *   set's parameters (template-set design 8.2).
 *
 * A row whose preview failed is left out (it cannot be checked in the first place).
 *
 * ```kotlin
 * generationItemsOf(checkedRows, state.form) // [item(feature.NewFeature), item(a, b of :app)]
 * ```
 */
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
