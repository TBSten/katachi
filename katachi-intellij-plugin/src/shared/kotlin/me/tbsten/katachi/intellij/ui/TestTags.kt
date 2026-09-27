package me.tbsten.katachi.intellij.ui

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.FieldId

/**
 * `Modifier.testTag`s of the parts the UI tests operate: the search field, a row's checkbox, an
 * input field and its `>`, and Generate. Nothing else reads them.
 */
internal object KatachiTestTags {
    const val SEARCH: String = "katachi.search"
    const val GENERATE: String = "katachi.generate"

    fun check(id: TemplateId): String = "katachi.check:${id.module.gradlePath}:${id.roleName}"

    fun field(id: FieldId): String = "katachi.field:${id.templateId.module.gradlePath}:${id.templateId.roleName}:${id.parameterName}"

    fun multiline(id: FieldId): String = "katachi.multiline:${id.templateId.module.gradlePath}:${id.templateId.roleName}:${id.parameterName}"
}
