package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.placement.PathPattern
import me.tbsten.katachi.intellij.data.placement.PatternPart
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import java.nio.file.Path

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

private fun setCandidateRequestsOf(row: ModuleTemplate, detail: TemplateDetailModel, inputs: Map<String, String>, root: Path): List<CandidateRequest> =
    fieldSlotsOf(detail, inputs).mapNotNull { slot ->
        val parameter = (slot as? FieldSlot.Shown)?.parameter as? ParameterModel.StringParam ?: return@mapNotNull null
        val pattern = setDirectoryLevelPatternOf(detail, parameter.name)?.let(PathPattern::parse) ?: return@mapNotNull null
        CandidateRequest(FieldId(row.id, parameter.name), pattern, parameter.name, inputs, root)
    }
