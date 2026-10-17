package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.placement.PathPattern
import me.tbsten.katachi.intellij.data.placement.PatternPart
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import java.nio.file.Path

/**
 * The capture fields of [row]'s form that offer directory candidates, as requests for [CaptureCandidateLoader]
 * (design 3.3): the shown directory captures (a module capture lists modules instead, a folded one is not on screen),
 * each with the pattern of the first file whose path holds it. [inputs] are the values entered so far, by name; [root] is
 * where the definition's patterns start. Empty when the row has no preview.
 *
 * ```kotlin
 * candidateRequestsOf(row, form.inputsOf(row.id), root) // [CandidateRequest(FieldId(row.id, "feature"), ..., "feature", ...)]
 * ```
 */
internal fun candidateRequestsOf(row: ModuleTemplate, inputs: Map<String, String>, root: Path): List<CandidateRequest> {
    val detail = row.template.detail ?: return emptyList()
    if (row.template.set != null) return setCandidateRequestsOf(row, detail, inputs, root)
    return fieldSlotsOf(detail, inputs).mapNotNull { slot ->
        val capture = (slot as? FieldSlot.Shown)?.parameter as? ParameterModel.CaptureParam ?: return@mapNotNull null
        if (capture.existingModules != null) return@mapNotNull null
        val pattern = detail.files.firstNotNullOfOrNull { file -> PathPattern.parse(file.pattern)?.takeIf { it.holds(capture.name) } }
            ?: return@mapNotNull null
        CandidateRequest(FieldId(row.id, capture.name), pattern, capture.name, inputs, root)
    }
}

private fun PathPattern.holds(capture: String): Boolean =
    segments.any { segment -> segment.parts.any { it is PatternPart.Capture && it.name == capture } }

/**
 * A template set's parameters that stand for a whole directory level of a member's file ([setDirectoryLevelPatternOf]): the
 * set's row has no captures, but its files speak in the set's parameter names (template-set 8.1, C-9), so the pattern of
 * the first such file is used as a capture's would be. A parameter in a file name, sharing its level with other text, or
 * only below a module capture is a plain field.
 */
private fun setCandidateRequestsOf(row: ModuleTemplate, detail: TemplateDetailModel, inputs: Map<String, String>, root: Path): List<CandidateRequest> =
    fieldSlotsOf(detail, inputs).mapNotNull { slot ->
        val parameter = (slot as? FieldSlot.Shown)?.parameter as? ParameterModel.StringParam ?: return@mapNotNull null
        val pattern = setDirectoryLevelPatternOf(detail, parameter.name)?.let(PathPattern::parse) ?: return@mapNotNull null
        CandidateRequest(FieldId(row.id, parameter.name), pattern, parameter.name, inputs, root)
    }
