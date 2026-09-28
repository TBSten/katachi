package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FormState
import me.tbsten.katachi.intellij.presentation.linkKeyOf

/**
 * What the dialog decides by itself; everything else in [GenerateDialogState] is derived from it
 * (`dialogStateOf`). Changed only on the EDT, by the pure functions of this file.
 */
internal data class DialogCore(
    /** The usable templates of every definition, in list order. */
    val all: List<ModuleTemplate>,
    val definition: ModuleId,
    val selectedId: TemplateId,
    /** The selected template as last seen: kept when a reload removes it, for its title and fields. */
    val selected: ModuleTemplate?,
    val form: FormState,
    /** The selected template's captures its origin decides (issue 1), recomputed per template (decision 4). */
    val seeds: Map<String, String>,
    val listNotice: GenerateDialogListNotice? = null,
) {
    val candidates: List<ModuleTemplate> get() = all.filter { it.module.id == definition }

    val inputs: Map<String, String> get() = form.inputsOf(selectedId)
}

/** The templates a dialog can offer: a form needs the details, so a template that did not preview is left out. */
internal fun usableTemplatesOf(templates: List<ModuleTemplate>): List<ModuleTemplate> = templates.filter { it.template.isAvailable }

/**
 * The dialog as it opens: [initial] selected with its [seeds] as the initial values; its definition
 * selected (decision 10). When [initial] is not usable, the first usable template stands in.
 */
internal fun initialCoreOf(all: List<ModuleTemplate>, initial: TemplateId, seedsFor: (TemplateId) -> Map<String, String>): DialogCore {
    val start = all.firstOrNull { it.id == initial } ?: all.firstOrNull { it.module.id == initial.module } ?: all.firstOrNull()
    val empty = DialogCore(all, start?.module?.id ?: initial.module, start?.id ?: initial, start, FormState(), emptyMap())
    return if (start == null) empty.copy(listNotice = GenerateDialogListNotice.NoCandidates) else switchTo(empty, start, seedsFor)
}

/**
 * Selects [template] (decision 4): a capture the origin decides for it takes that value again
 * (the seeds are asked for the new template); any other field keeps the value of the same-named
 * field of the previous template when both link (same name, kind and type, as the tool window links
 * fields, E-14); the rest start empty.
 */
internal fun switchTo(core: DialogCore, template: ModuleTemplate, seedsFor: (TemplateId) -> Map<String, String>): DialogCore {
    val seeds = seedsFor(template.id)
    val detail = template.template.detail
    val inputs = if (detail == null) emptyMap() else carriedInputsOf(core.selected?.template?.detail, core.inputs, detail, seeds)
    val form = FormState(selected = listOf(template.id), expanded = setOf(template.id), inputs = mapOf(template.id to inputs))
    return core.copy(definition = template.module.id, selectedId = template.id, selected = template, form = form, seeds = seeds)
}

private fun carriedInputsOf(from: TemplateDetailModel?, inputs: Map<String, String>, to: TemplateDetailModel, seeds: Map<String, String>): Map<String, String> {
    val keys = from?.let { detail -> allParametersOf(detail).associate { it.name to linkKeyOf(it) } }.orEmpty()
    val carried = LinkedHashMap<String, String>()
    for (parameter in allParametersOf(to)) {
        val seed = seeds[parameter.name]
        val own = inputs[parameter.name]
        when {
            seed != null -> carried[parameter.name] = seed
            own != null && keys[parameter.name]?.let { it == linkKeyOf(parameter) } == true -> carried[parameter.name] = own
        }
    }
    return carried
}

/** The user picked [id] in the template select box. */
internal fun selectTemplate(core: DialogCore, id: TemplateId, seedsFor: (TemplateId) -> Map<String, String>): DialogCore {
    if (id == core.selectedId && core.selected != null && core.all.any { it.id == id }) return core
    val template = core.all.firstOrNull { it.id == id } ?: return core
    return switchTo(core, template, seedsFor).copy(listNotice = null)
}

/** The user picked [definition] (issue 7): its templates become the candidates and the first is selected. */
internal fun selectDefinition(core: DialogCore, definition: ModuleId, seedsFor: (TemplateId) -> Map<String, String>): DialogCore {
    if (definition == core.definition) return core
    val first = core.all.firstOrNull { it.module.id == definition } ?: return core
    return switchTo(core, first, seedsFor).copy(listNotice = null)
}

/** The user typed [value] into the field [name] of the selected template. */
internal fun inputOf(core: DialogCore, name: String, value: String): DialogCore =
    core.copy(form = core.form.withInput(FieldId(core.selectedId, name), value))

/**
 * The template list changed while the dialog was open:
 *
 * - the selected template is still there: it stays selected with its inputs, the new version of it
 *   (a field it gained takes its seed, when the origin decides one);
 * - it is gone: the first template of the same definition is selected by [switchTo]'s rules, with
 *   [GenerateDialogListNotice.TemplateReplaced];
 * - the definition has no template left: nothing is selected, [GenerateDialogListNotice.NoCandidates]
 *   (and [Generate] cannot be pressed), until a later list brings one back.
 */
internal fun listChanged(core: DialogCore, templates: List<ModuleTemplate>, seedsFor: (TemplateId) -> Map<String, String>): DialogCore {
    val all = usableTemplatesOf(templates)
    val current = all.firstOrNull { it.id == core.selectedId }
    if (current != null) {
        val detail = current.template.detail
        val inputs = core.inputs
        val gained = detail?.let { allParametersOf(it) }.orEmpty()
            .filter { it.name !in inputs }
            .mapNotNull { parameter -> core.seeds[parameter.name]?.let { parameter.name to it } }
        val form = core.form.copy(inputs = core.form.inputs + (core.selectedId to inputs + gained))
        val notice = core.listNotice.takeUnless { it == GenerateDialogListNotice.NoCandidates }
        return core.copy(all = all, selected = current, form = form, listNotice = notice)
    }
    val replacement = all.firstOrNull { it.module.id == core.definition }
        ?: return core.copy(all = all, listNotice = GenerateDialogListNotice.NoCandidates)
    val goneTitle = core.selected?.template?.title.orEmpty()
    return switchTo(core.copy(all = all), replacement, seedsFor).copy(listNotice = GenerateDialogListNotice.TemplateReplaced(goneTitle))
}
