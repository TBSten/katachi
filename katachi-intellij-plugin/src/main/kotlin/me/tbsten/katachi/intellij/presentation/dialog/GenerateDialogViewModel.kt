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

// Dispatched on the EDT: scope runs on it, so the state is only written there.
internal class GenerateDialogViewModel(
    private val scope: CoroutineScope,
    private val request: GenerateDialogRequest,
    candidates: List<ModuleTemplate>,
    private val seeds: CaptureSeedPort,
    private val checkTarget: suspend (Path?) -> TargetState,
    private val rootOf: (KatachiModule) -> Path,
    private val checkContext: CoroutineContext = Dispatchers.Default,
    private val settle: suspend () -> Unit = { delay(CHECK_DEBOUNCE_MILLIS) },
    private val onUnexpectedCheckFailure: (Throwable) -> Unit = {},
    candidateLister: DirectoryLister = DirectoryLister { emptyList() },
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
        const val CHECK_DEBOUNCE_MILLIS: Long = 300
    }
}
