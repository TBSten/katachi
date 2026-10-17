package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel

/**
 * The pattern of the first member file that holds the set parameter [name] as a whole directory level (`feature/${name}/ui/...`),
 * or `null` when none does. Only files with a fixed `path` count (template-set 8.2): a member's file below a module capture
 * (`path == null`) speaks of modules, which katachi refuses to create, so no directory is asked of it.
 *
 * ```kotlin
 * setDirectoryLevelPatternOf(detail, "feature") // "feature/${feature}/ui/Screen.kt"
 * ```
 */
internal fun setDirectoryLevelPatternOf(detail: TemplateDetailModel, name: String): String? =
    detail.files.filter { it.path != null && it.modulePlacement == null }
        .firstNotNullOfOrNull { file -> file.pattern.takeIf { holdsWholeLevel(it, name) } }

/** Whether the first level that mentions `${name}` is nothing but it, and not the file name. */
private fun holdsWholeLevel(pattern: String, name: String): Boolean {
    val segments = pattern.split('/')
    val level = segments.indexOfFirst { "\${$name}" in it }
    return level in 0 until segments.lastIndex && segments[level] == "\${$name}"
}

/**
 * The error of [parameter] holding [input] in [row]'s form. A template set's parameter that stands for a whole directory level
 * is the member's capture value as it is, so it must be one level too (template-set design 4: the member's exception is thrown
 * as it is); every other parameter is judged by [validateField] alone.
 */
internal fun fieldErrorOf(row: ModuleTemplate, detail: TemplateDetailModel, parameter: ParameterModel, input: String?): FieldError? {
    validateField(parameter, input)?.let { return it }
    if (row.template.set == null || parameter !is ParameterModel.StringParam) return null
    if (setDirectoryLevelPatternOf(detail, parameter.name) == null) return null
    return captureProblemOf(input.orEmpty())?.let(FieldError::InvalidCapture)
}
