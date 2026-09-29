package me.tbsten.katachi.intellij.uitest.dialog

import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.presentation.FieldSlot
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogState
import me.tbsten.katachi.intellij.presentation.dialog.TargetNotice
import me.tbsten.katachi.intellij.presentation.dialog.dialogUiStateOf
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.validateField
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import me.tbsten.katachi.intellij.uitest.pbt.DiskKind
import java.nio.file.Path

/** What the invariants read at one moment: the ViewModel's state and the world it stands in. */
internal class DialogView(
    val state: GenerateDialogState,
    /** Every template of the shared list, in list order, whether or not the dialog can offer it. */
    val listed: List<ModuleTemplate>,
    val index: TemplatePlacementIndex,
    val origin: EntryOrigin,
    val disk: Map<Path, DiskKind>,
    /** The dialog was cancelled: nothing is checked any more, so the notice may be old. */
    val cancelled: Boolean,
) {
    val usable: List<ModuleTemplate> get() = listed.filter { it.template.isAvailable }
    val selected: ModuleTemplate? get() = state.selected
    val values: Map<String, String?> get() = state.fields.associate { it.name to it.value }
    val seeds: Map<String, String> get() = index.seedsFor(origin, state.selectedTemplate)
}

private fun DialogView.originSegments(): List<String> {
    val root = selected?.module?.linkedRootPath ?: return emptyList()
    return if (origin.path.startsWith(root)) root.relativize(origin.path).map { it.toString() }.filter { it.isNotEmpty() } else emptyList()
}

/** What must hold in every state the dialog is in once its checks have answered. */
internal fun stateProblemsOf(view: DialogView): List<String> {
    val problems = mutableListOf<String>()
    val s = view.state
    val usable = view.usable

    // The select boxes: every candidate of the definition, one is enough for a select box, two definitions or more for that box.
    val expectedCandidates = usable.filter { it.module.id == s.selectedTemplate.module }.map { it.id }
    if (s.candidates.map { it.id } != expectedCandidates) problems += "candidates ${s.candidates.map { it.id.template }} != the usable templates of the selected definition ${expectedCandidates.map { it.template }}"
    val ui = dialogUiStateOf(s, PropertiesGenerateDialogStrings.english())
    if (ui.templateOptions.size != s.candidates.size) problems += "the template select box lists ${ui.templateOptions.size} for ${s.candidates.size} candidates"
    if (s.candidates.isNotEmpty() && s.selected == null) problems += "candidates but none selected"
    if (s.selected != null && s.candidates.getOrNull(ui.selectedTemplate)?.id != s.selectedTemplate) problems += "the select box points at ${ui.selectedTemplate}, not at the selected template"
    val definitions = usable.map { it.module }.distinctBy { it.id }
    if (definitions.size >= 2) {
        if (s.definitions.map { it.id } != definitions.map { it.id }) problems += "definitions ${s.definitions.map { it.gradlePath }} != ${definitions.map { it.gradlePath }}"
        if (s.selectedDefinition != s.selectedTemplate.module) problems += "the selected definition ${s.selectedDefinition} is not the selected template's"
    } else if (s.definitions.isNotEmpty() || s.selectedDefinition != null || ui.definitionOptions.isNotEmpty()) {
        problems += "a definition select box with ${definitions.size} definition(s)"
    }
    if (ui.targetPath != s.targetPath || ui.canGenerate != s.canGenerate) problems += "the UI state differs from the state"

    val selected = s.selected
    val detail = selected?.template?.detail
    if (selected == null || detail == null) {
        if (s.fields.isNotEmpty() || s.targetPath.isNotEmpty() || s.target != null) problems += "fields or a target without a template"
        if (s.canGenerate) problems += "can generate without a template"
        return problems
    }

    // The form: every field of the template once, the captures first; the origin's captures are initial values.
    val names = allParametersOf(detail).map { it.name }
    if (s.fields.map { it.name }.toSet() != names.toSet()) problems += "fields ${s.fields.map { it.name }} != $names"
    if (s.fields.size != names.size) problems += "a field twice or missing: ${s.fields.map { it.name }} for $names"
    val captureNames = detail.captures.map { it.name }
    if (s.fields.take(captureNames.size).map { it.name } != captureNames) problems += "captures are not the first fields: ${s.fields.map { it.name }}"
    val seeds = view.seeds
    val seeded = s.fields.filter { it.seeded }.map { it.name }.toSet()
    if (seeded != seeds.keys) problems += "seeded fields $seeded != the origin's decided captures ${seeds.keys}"

    // The sample path: the pattern with the inputs put in -- below a module capture katachi describes,
    // katachi's own path in the module the inputs name, once they name an existing one.
    val file = detail.files.first()
    val picked = file.modulePlacement?.let { placement ->
        placement.modules.firstOrNull { module -> placement.captureNames.map { view.values[it] } == module.values }
    }
    val pattern = picked?.path ?: file.pattern
    val expectedPath = expectedTargetPath(pattern, { view.values[it] }, seeds, view.originSegments())
    if (s.targetPath != expectedPath) problems += "sample path '${s.targetPath}' != '$expectedPath'"

    // Generate can be pressed exactly when every shown field is fine and the target has no content.
    val valid = s.fields.all { it.slot !is FieldSlot.Shown || validateField(it.slot.parameter, it.value) == null }
    val complete = valid && !expectedPath.contains("\${")
    val root = selected.module.linkedRootPath
    val decided = complete && !hasDerivedLeft(expectedPath)
    if (decided && s.target != root.resolve(expectedPath)) problems += "target ${s.target} != ${root.resolve(expectedPath)}"
    if (!decided && s.target != null) problems += "a target ${s.target} for an undecided path '$expectedPath'"
    if (view.cancelled) return problems

    // The notice is the pre-check's answer for what is on disk now (issue 6).
    val kind = if (decided) view.disk[root.resolve(expectedPath)] ?: DiskKind.Missing else null
    val expectedNotice = when {
        kind != null -> noticeOf(kind)
        complete -> TargetNotice.DecidedByKatachi
        else -> null
    }
    if (s.targetNotice != expectedNotice) problems += "notice ${s.targetNotice} != $expectedNotice (disk: $kind)"
    if (kind != null && s.targetState != answerOf(kind)) problems += "target state ${s.targetState} != ${answerOf(kind)}"
    val expectedGenerate = complete && kind != DiskKind.Content
    if (s.canGenerate != expectedGenerate) problems += "canGenerate ${s.canGenerate} != $expectedGenerate (valid=$valid, path='$expectedPath', disk=$kind)"
    return problems
}
