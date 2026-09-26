package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.allParametersOf

/** Fields link when name and type agree, across modules too (E-14, E-15). */
internal data class LinkKey(val name: String, val kindName: String, val typeName: String)

internal fun linkKeyOf(parameter: ParameterModel): LinkKey? =
    if (parameter is ParameterModel.UnknownParam) null else LinkKey(parameter.name, parameter.kindName, parameter.typeName)

/*
 * The link rules, provisionally decided where the spec is silent:
 *
 * - A link group is every field of the checked rows with the same LinkKey that is not unlinked.
 * - The first field typed into while the group has no source becomes the group's source. Typing
 *   into the source copies the value to every linked field of the group.
 * - Typing into another linked field breaks only that field's link (it joins `unlinked`), as the
 *   spec says "rewriting a linked field to a different value unlinks just that field".
 * - Relinking a field copies the group's value into it.
 * - A row checked later fills its empty fields from the group and links them; a field it already
 *   has a different value in stays unlinked. Unchecked rows neither send nor receive.
 */

/** The fields of the checked rows that share [key] and are still linked, in list order. */
internal fun linkedFieldsOf(key: LinkKey, templates: List<ModuleTemplate>, form: FormState): List<FieldId> =
    templates.filter { form.isSelected(it.id) }.flatMap { row ->
        val detail = row.template.detail ?: return@flatMap emptyList()
        allParametersOf(detail).filter { linkKeyOf(it) == key }.map { FieldId(row.id, it.name) }
    }.filter { it !in form.unlinked }

/** Whether [field] shows 🔗: it is linked and some other checked row has a field of its group. */
internal fun isLinked(field: FieldId, templates: List<ModuleTemplate>, form: FormState): Boolean {
    if (field in form.unlinked) return false
    val key = keyOf(field, templates) ?: return false
    return linkedFieldsOf(key, templates, form).any { it != field && it.templateId != field.templateId }
}

/** Whether [field] shows the faint unlink icon (E-14). */
internal fun isUnlinked(field: FieldId, form: FormState): Boolean = field in form.unlinked

/** The user typed [value] into [field]. */
internal fun inputField(form: FormState, templates: List<ModuleTemplate>, field: FieldId, value: String): FormState {
    val key = keyOf(field, templates)
    if (key == null || field in form.unlinked || !form.isSelected(field.templateId)) return form.withInput(field, value)
    val group = linkedFieldsOf(key, templates, form)
    val source = form.linkSources[key]?.takeIf { it in group }
    return if (source == null || source == field) {
        group.fold(form.copy(linkSources = form.linkSources + (key to field))) { acc, member -> acc.withInput(member, value) }
    } else if (value == form.inputOf(source)) {
        form.withInput(field, value)
    } else {
        form.withInput(field, value).copy(unlinked = form.unlinked + field)
    }
}

/** The unlink icon was pressed: [field] rejoins its group and takes the group's value. */
internal fun relinkField(form: FormState, templates: List<ModuleTemplate>, field: FieldId): FormState {
    val relinked = form.copy(unlinked = form.unlinked - field)
    val key = keyOf(field, templates) ?: return relinked
    val value = groupValueOf(key, templates, relinked, except = field) ?: return relinked
    return relinked.withInput(field, value)
}

/** [templateId] was just checked: fill its empty fields from their groups, unlink the differing ones. */
internal fun joinLinks(form: FormState, templates: List<ModuleTemplate>, templateId: TemplateId): FormState {
    val row = templates.firstOrNull { it.id == templateId } ?: return form
    val detail = row.template.detail ?: return form
    var result = form
    for (parameter in allParametersOf(detail)) {
        val key = linkKeyOf(parameter) ?: continue
        val field = FieldId(templateId, parameter.name)
        val groupValue = groupValueOf(key, templates, result, except = field) ?: continue
        val own = result.inputOf(field)
        result = when {
            own.isNullOrEmpty() -> result.withInput(field, groupValue).copy(unlinked = result.unlinked - field)
            own != groupValue -> result.copy(unlinked = result.unlinked + field)
            else -> result
        }
    }
    return result
}

/** The value the linked fields of [key] hold: the source's, else the first non-empty one. */
private fun groupValueOf(key: LinkKey, templates: List<ModuleTemplate>, form: FormState, except: FieldId): String? {
    val group = linkedFieldsOf(key, templates, form).filter { it != except }
    val source = form.linkSources[key]?.takeIf { it in group }
    return (listOfNotNull(source) + group).firstNotNullOfOrNull { member -> form.inputOf(member)?.takeIf { it.isNotEmpty() } }
}

private fun keyOf(field: FieldId, templates: List<ModuleTemplate>): LinkKey? {
    val detail = templates.firstOrNull { it.id == field.templateId }?.template?.detail ?: return null
    return allParametersOf(detail).firstOrNull { it.name == field.parameterName }?.let(::linkKeyOf)
}
