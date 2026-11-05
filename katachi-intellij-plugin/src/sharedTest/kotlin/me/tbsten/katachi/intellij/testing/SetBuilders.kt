package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.SetArgModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateSetMemberModel
import me.tbsten.katachi.intellij.model.TemplateSetModel
import me.tbsten.katachi.intellij.model.TemplateSummaryModel

/** A member of a [setTemplate]: the template it runs, and the files it writes in the set's parameter names. */
internal fun setMember(template: String, title: String, vararg paths: String, args: List<SetArgModel> = emptyList()) =
    TemplateSetMemberModel(template, title, args, paths.map { path -> file(path.substringAfterLast('/'), path = path, pattern = path, captures = Regex("""\$\{([^}]+)}""").findAll(path).map { it.groupValues[1] }.distinct().toList()) })

/**
 * A template set's row as the parser makes it (template-set design 8.1): its specifier is also its [TemplateModel.roleName],
 * its files every member's files in order, and it has no captures.
 */
internal fun setTemplate(
    specifier: String,
    members: List<TemplateSetMemberModel>,
    parameters: List<ParameterModel> = listOf(stringParam("feature")),
    title: String = specifier.substringAfterLast('.'),
    unavailable: Boolean = false,
): TemplateModel {
    val summary = TemplateSummaryModel(specifier, null, title, specifier, null, parameters.map { it.name }, conflict = unavailable)
    val detail = TemplateDetailModel(
        specifier,
        null,
        title,
        specifier,
        null,
        parameters,
        members.flatMap { it.files },
        emptyList(),
        "./gradlew katachiTemplate --arg template=$specifier",
    )
    return TemplateModel(summary, detail.takeUnless { unavailable }, TemplateSetModel(specifier.substringAfterLast('.'), members))
}
