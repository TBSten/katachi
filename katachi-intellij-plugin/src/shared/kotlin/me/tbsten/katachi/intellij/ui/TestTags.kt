package me.tbsten.katachi.intellij.ui

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.FieldId

/**
 * `Modifier.testTag`s of the parts the UI tests operate: the search field, a row's checkbox, an
 * input field, its note and its `>`, Generate, the list and its highlighted row, and the generate
 * dialog's parts. Nothing else reads them.
 */
internal object KatachiTestTags {
    const val SEARCH: String = "katachi.search"
    const val GENERATE: String = "katachi.generate"

    /** The scrolling list, and the header of the row [View template] highlighted (C2). */
    const val LIST: String = "katachi.list"
    const val HIGHLIGHTED: String = "katachi.highlighted"

    /** The generate dialog's template select box, definition select box, form, path and notices. */
    const val DIALOG_TEMPLATE: String = "katachi.dialog.template"
    const val DIALOG_DEFINITION: String = "katachi.dialog.definition"
    const val DIALOG_FORM: String = "katachi.dialog.form"
    const val DIALOG_TARGET_PATH: String = "katachi.dialog.targetPath"
    const val DIALOG_TARGET_NOTICE: String = "katachi.dialog.targetNotice"
    const val DIALOG_LIST_NOTICE: String = "katachi.dialog.listNotice"
    const val DIALOG_REFUSAL: String = "katachi.dialog.refusal"

    fun check(id: TemplateId): String = "katachi.check:${id.module.gradlePath}:${id.template}"

    fun field(id: FieldId): String = "katachi.field:${id.templateId.module.gradlePath}:${id.templateId.template}:${id.parameterName}"

    fun hint(id: FieldId): String = "katachi.hint:${id.templateId.module.gradlePath}:${id.templateId.template}:${id.parameterName}"

    fun multiline(id: FieldId): String = "katachi.multiline:${id.templateId.module.gradlePath}:${id.templateId.template}:${id.parameterName}"
}
