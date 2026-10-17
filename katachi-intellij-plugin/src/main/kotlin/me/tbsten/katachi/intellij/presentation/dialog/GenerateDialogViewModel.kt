package me.tbsten.katachi.intellij.presentation.dialog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.data.generate.templateArgsOf
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.CandidateList
import me.tbsten.katachi.intellij.presentation.CaptureCandidateLoader
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.candidateRequestsOf
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

/**
 * The generate dialog's ViewModel, IntelliJ-free (issue 9), so the dialog's behaviour is tested
 * without an IDE: template and definition selection, the form with the decided captures as editable
 * initial values (issue 1), the target path, the existing-file notice (issue 6) and whether
 * [Generate] can be pressed. Generating makes one file from one template (issue 2).
 *
 * The rules (the transitions are in `DialogTransitions.kt`):
 *
 * - **Candidates**: every usable template of every definition is offered, not only those that fit
 *   the origin (issue 0); the request's template starts selected, in its own definition (decision 10).
 *   The definition select box shows with two or more definitions; picking one offers its templates.
 * - **Switching templates** (decision 4): the captures the origin decides are asked of [seeds] for the
 *   new template; the other fields keep the value of the same-named, linking field of the previous
 *   template; the rest start empty.
 * - **The list changes while open** ([GenerateDialogIntent.ListChanged]): the selected template stays
 *   when it is still there; when it is gone the first template of the same definition takes its
 *   place with a notice; with none left [Generate] cannot be pressed.
 * - **The existing-file check** ([checkTarget], the same check [Generate] runs) is asked again when
 *   the target file changes (an input or a selection), when the list changes, and when files were
 *   written outside the dialog ([GenerateDialogIntent.FilesChangedOutside]). It runs on
 *   [checkContext], after [settle] (typing in bursts checks once), and a newer check cancels an older one.
 *
 * Dispatched on the EDT: [scope] runs on it, so the state is only written there.
 *
 * ```kotlin
 * val vm = GenerateDialogViewModel(scope, request, templatesOf(snapshots), seeds, generation::checkTarget, rootOf)
 * vm.dispatch(GenerateDialogIntent.Input("name", "Profile"))
 * vm.generationRequest() // one template (issue 2), or null while [Generate] cannot be pressed
 * ```
 */
internal class GenerateDialogViewModel(
    private val scope: CoroutineScope,
    private val request: GenerateDialogRequest,
    /** Every template of every definition, in list order. */
    candidates: List<ModuleTemplate>,
    private val seeds: CaptureSeedPort,
    /** `SingleFileGeneration.checkTarget`: the same check [Generate] runs (issue 6). */
    private val checkTarget: suspend (Path?) -> TargetState,
    /**
     * The directory a definition's patterns are relative to (`placementRootOf`, which checks files).
     * Remembered per definition here and forgotten after a list change or an outside write; how often it
     * really reads the disk is the frame's choice (the real one finds each root once, when the dialog opens).
     */
    private val rootOf: (KatachiModule) -> Path,
    private val checkContext: CoroutineContext = Dispatchers.Default,
    private val settle: suspend () -> Unit = { delay(CHECK_DEBOUNCE_MILLIS) },
    /** Told when checking the target failed unexpectedly (the frame logs it; the ViewModel knows no logger of the IDE). */
    private val onUnexpectedCheckFailure: (Throwable) -> Unit = {},
    /** Reads the directories behind the capture fields' candidates (design 3.3), on [checkContext] after [settle]; none by default. */
    candidateLister: DirectoryLister = DirectoryLister { emptyList() },
    /** Told when reading the directories failed (the frame logs it). */
    onCandidateFailure: (Throwable) -> Unit = {},
) {
    private var core: DialogCore = initialCoreOf(usableTemplatesOf(candidates), request.initialTemplate, ::seedsOf)
    private var checked: Pair<Path, TargetState>? = null
    private var checking: Job? = null
    private var checkingTarget: Path? = null
    private val roots = HashMap<ModuleId, Path>()

    private var captureCandidates: Map<FieldId, CandidateList> = emptyMap()
    private val candidateLoader = CaptureCandidateLoader(
        scope = scope,
        lister = candidateLister,
        ioContext = checkContext,
        settle = settle,
        onFailure = onCandidateFailure,
        onLoaded = { field, list ->
            captureCandidates = if (list == null) captureCandidates - field else captureCandidates + (field to list)
            mutableState.value = stateOf()
        },
    )
    private val requestedFields = HashSet<FieldId>()

    /** The String fields shown as a text area (design D-2); dropped when the selected template changes. */
    private var multiline: Set<String> = emptySet()

    private val mutableState = MutableStateFlow(stateOf())

    val state: StateFlow<GenerateDialogState> = mutableState.asStateFlow()

    init {
        recheckIfMoved(force = true)
        syncCandidates(force = false)
    }

    fun dispatch(intent: GenerateDialogIntent) {
        if (intent is GenerateDialogIntent.ListChanged || intent == GenerateDialogIntent.FilesChangedOutside) roots.clear()
        val before = core.selectedId
        core = when (intent) {
            is GenerateDialogIntent.SelectTemplate -> selectTemplate(core, intent.template, ::seedsOf)
            is GenerateDialogIntent.SelectDefinition -> selectDefinition(core, intent.definition, ::seedsOf)
            is GenerateDialogIntent.Input -> inputOf(core, intent.name, intent.value)
            is GenerateDialogIntent.ListChanged -> listChanged(core, intent.templates, ::seedsOf)
            is GenerateDialogIntent.ToggleMultiline -> {
                multiline = if (intent.name in multiline) multiline - intent.name else multiline + intent.name
                core
            }
            GenerateDialogIntent.FilesChangedOutside -> core
        }
        if (core.selectedId != before) multiline = emptySet()
        mutableState.value = stateOf()
        val force = intent is GenerateDialogIntent.ListChanged || intent == GenerateDialogIntent.FilesChangedOutside
        recheckIfMoved(force)
        syncCandidates(force)
    }

    /**
     * Asks for the directory candidates of the selected template's shown capture fields and lets go of the others'
     * (design 3.3): when the field appears (the template was selected), when a value above it changes, and, [force]d,
     * when the files changed or the list was reloaded. The loader ignores a request whose key did not change.
     */
    private fun syncCandidates(force: Boolean) {
        val state = mutableState.value
        val template = state.selected
        val wanted = if (template == null) {
            emptyList()
        } else {
            candidateRequestsOf(template, state.form.inputsOf(template.id), rootOfCached(template.module))
        }
        val wantedFields = wanted.map { it.field }.toSet()
        for (field in requestedFields - wantedFields) candidateLoader.forget(field)
        requestedFields.clear()
        requestedFields.addAll(wantedFields)
        for (request in wanted) candidateLoader.request(request, force)
        if (captureCandidates.keys.any { it !in wantedFields }) {
            captureCandidates = captureCandidates.filterKeys { it in wantedFields }
            mutableState.value = stateOf()
        }
    }

    /** What [Generate] runs: the selected template with the inputs; `null` while it cannot be pressed. */
    fun generationRequest(): SingleFileGenerationRequest? {
        val state = mutableState.value
        val template = state.selected ?: return null
        val detail = template.template.detail ?: return null
        if (!state.canGenerate) return null
        // templateArgsOf starts with `template` and `onExisting`; the generation adds its own (decision 18).
        val args = templateArgsOf(template.template.template, detail, core.inputs, OnExistingChoice.Overwrite).drop(2)
        return SingleFileGenerationRequest(template, request.origin, args, state.target)
    }

    private fun seedsOf(template: TemplateId): Map<String, String> =
        if (template == request.initialTemplate) request.seeds else seeds.seedsFor(request.origin, template)

    private fun rootOfCached(module: KatachiModule): Path = roots.getOrPut(module.id) { rootOf(module) }

    private fun stateOf(): GenerateDialogState =
        dialogStateOf(core, request, ::rootOfCached, checked).copy(captureCandidates = captureCandidates, multiline = multiline)

    /** Starts the existing-file check when the target is not the one checked (or being checked), or when [force]d. */
    private fun recheckIfMoved(force: Boolean) {
        val target = mutableState.value.target
        if (!force && target == checkingTarget) return
        checking?.cancel()
        checkingTarget = target
        if (target == null) return
        checking = scope.launch {
            val answer = withContext(checkContext) {
                settle()
                try {
                    checkTarget(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Unknown: [Generate] checks again before writing (E3), and says why it stops.
                    onUnexpectedCheckFailure(e)
                    null
                }
            } ?: return@launch
            checked = target to answer
            mutableState.value = stateOf()
        }
    }

    companion object {
        /** How long typing must pause before the target is checked. */
        const val CHECK_DEBOUNCE_MILLIS: Long = 300
    }
}
