package me.tbsten.katachi.intellij.presentation

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.detect.SyncedProjectSource
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.data.generate.GenerationItem
import me.tbsten.katachi.intellij.data.generate.GenerationListener
import me.tbsten.katachi.intellij.data.generate.GenerationSession
import me.tbsten.katachi.intellij.data.generate.filesToOpen
import me.tbsten.katachi.intellij.data.generate.templateArgsOf
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.gradle.SerialGradleTaskRunner
import me.tbsten.katachi.intellij.data.load.LoadResult
import me.tbsten.katachi.intellij.data.load.TemplateDescriptionLoader
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import java.nio.file.Path

/**
 * Holds the tool window's [state] and carries out [KatachiIntent]s: pure ones through
 * [applyFormIntent], the rest through the ports (synced data, Gradle, disk, IDE).
 *
 * All work runs in [scope], which the project service owns: closing the project cancels it, and
 * with it the running Gradle build (E-49). [dispatch] is called on the EDT; the work it starts runs
 * in the background, so every state change goes through `update`, and the synced data and the cached
 * JSON are read on [ioDispatcher]. Gradle runs one build at a time ([SerialGradleTaskRunner]).
 */
internal class KatachiToolWindowViewModel(
    private val scope: CoroutineScope,
    private val syncedProject: SyncedProjectSource,
    runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
    private val effects: IdeEffects,
    /** What the project remembers (the "existing files" combo, folded modules) before anything loads. */
    initial: KatachiScreenState = KatachiScreenState(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<KatachiScreenState> = mutableState.asStateFlow()

    private val runner: GradleTaskRunner = SerialGradleTaskRunner(runner)
    private val loader = TemplateDescriptionLoader(this.runner, fileSystem)

    // Written on the EDT or in the background, read on the other.
    @Volatile private var detection: DetectionResult? = null

    @Volatile private var loadJob: Job? = null

    @Volatile private var generationJob: Job? = null

    @Volatile private var session: GenerationSession? = null

    @Volatile private var generationLabel: String? = null

    /** The files the latest generation writes or may write, absolute (E-44: not a definition change). */
    @Volatile private var ownWrites: Set<Path> = emptySet()

    private var opened = false
    private val ideIntents = IdeIntentHandler(scope, this.runner, fileSystem, effects, { mutableState.value }, { mutableState.update(it) })

    /** The definition modules found by the latest detection, for watching their files (E-44). */
    val definitionModules: List<KatachiModule> get() = mutableState.value.modules

    /** Whether [path] is a file the latest generation writes, whose VFS event is not a definition change. */
    fun isOwnWrite(path: Path): Boolean = path in ownWrites

    fun dispatch(intent: KatachiIntent) {
        var applied = false
        // update, not read-then-write: a load or a generation may be updating the state meanwhile.
        mutableState.update { current ->
            val next = applyFormIntent(current, intent)
            applied = next != null
            if (next == null) current else withExistingPaths(next)
        }
        if (applied) return
        if (ideIntents.handle(intent)) return
        when (intent) {
            KatachiIntent.Opened -> if (!opened) {
                opened = true
                detectAndLoad()
            }
            // Only after the tool window was shown: a sync alone never runs Gradle for someone who does not look (spec 04).
            KatachiIntent.SyncCompleted -> if (opened && mutableState.value.phase !is ScreenPhase.Ready) detectAndLoad()
            KatachiIntent.Reload -> reload()
            KatachiIntent.CancelLoad -> loadJob?.cancel()
            KatachiIntent.Generate -> generate()
            KatachiIntent.CancelGeneration -> cancelGeneration()
            KatachiIntent.RetryRemaining -> finishResult { state, report -> retryRemaining(state.form, report) }
            KatachiIntent.ContinueGenerating -> finishResult { state, _ -> continueGenerating(state.form, state.rows) }
            KatachiIntent.UncheckAll -> finishResult { state, _ -> uncheckAll(state.form) }
            else -> Unit
        }
    }

    private fun reload() {
        val current = mutableState.value
        if (current.generation is GenerationState.Running || loadJob?.isActive == true) return
        launchLoad(detect = detection !is DetectionResult.Found && detection !is DetectionResult.TaskListMissing)
    }

    private fun detectAndLoad() {
        if (loadJob?.isActive == true) return
        launchLoad(detect = true)
    }

    /** Detects first when [detect], then loads. Whatever goes wrong, `loading` does not stay set. */
    private fun launchLoad(detect: Boolean) {
        loadJob = scope.launch {
            try {
                if (detect && !detect()) return@launch
                load()
            } catch (e: CancellationException) {
                mutableState.update(::applyLoadCancelled)
                throw e
            } catch (e: Exception) {
                LOG.warn("Loading the katachi templates failed unexpectedly", e)
                mutableState.update { applyLoadFailed(it, LoadFailure.Gradle(GradleFailure.Other(listOf(messageOf(e)))), emptyList()) }
            }
        }
    }

    /** Reads the synced data and shows the cached list; `false` when there is nothing to load. */
    private suspend fun detect(): Boolean {
        mutableState.update { it.copy(phase = if (it.snapshots.isEmpty()) ScreenPhase.Initializing else it.phase) }
        // Walking the synced data and parsing the cached JSON are too slow for the EDT (a large Android build).
        val (result, cached) = withContext(ioDispatcher) {
            val found = detectDefinitionModules(syncedProject.read())
            found to if (found is DetectionResult.Found) loader.readCached(found.modules, found.katachiVersions) else emptyList()
        }
        detection = result
        val modules = when (result) {
            is DetectionResult.Found -> result.modules
            is DetectionResult.TaskListMissing -> result.candidates
            else -> {
                mutableState.update { it.copy(phase = ScreenPhase.Empty(emptyReasonOf(result)), modules = emptyList()) }
                return false
            }
        }
        mutableState.update { state ->
            if (cached.isEmpty()) state.copy(modules = modules) else applyLoaded(state.copy(modules = modules), cached)
        }
        return true
    }

    private suspend fun load() {
        mutableState.update { it.copy(loading = LoadingState(isInitial = it.snapshots.isEmpty())) }
        val listener = object : GradleRunListener {
            override fun onTask(taskPath: String) {
                mutableState.update { it.copy(loading = it.loading?.copy(currentTask = taskPath)) }
            }
        }
        effects.saveAllDocuments()
        val modules = mutableState.value.modules
        val result = when (val found = detection) {
            is DetectionResult.TaskListMissing -> loader.loadCandidates(found.candidates, versions(), listener)
            else -> loader.load(modules, versions(), listener)
        }
        mutableState.update { state ->
            when (result) {
                is LoadResult.Loaded -> withExistingPaths(applyLoaded(state, result.snapshots))
                is LoadResult.Failed -> applyLoadFailed(state, result.failure, result.output)
            }
        }
        if (result is LoadResult.Failed) effects.notifyLoadFailed()
    }

    private fun generate() {
        val current = mutableState.value
        if (current.generation != null) return
        // A background refresh over a cache does not block (E-41): generation waits for it instead.
        if (current.generateBlocker != null) return
        val refreshing = loadJob?.isActive == true
        generationLabel = null
        mutableState.update { if (it.generation == null) startGeneration(it, waitingForLoad = refreshing) else it }
        generationJob = scope.launch {
            try {
                loadJob?.join()
                val state = mutableState.value
                if (generateBlockerOf(state.rows, state.form, BusyState.Idle) != null) {
                    // The refreshed list no longer allows it (a template or a parameter changed).
                    mutableState.update { it.copy(generation = null) }
                    return@launch
                }
                runGeneration(state)
            } catch (e: CancellationException) {
                // cancelGeneration() already left the running state, or the project is closing.
                throw e
            } catch (e: Exception) {
                LOG.warn("Generating from katachi templates failed unexpectedly", e)
                val failure = GenerationFailure.NotReached(GradleFailure.Other(listOf(messageOf(e))))
                mutableState.update { abortGeneration(it, failure, generationLabel) }
            } finally {
                session = null
            }
        }
    }

    private suspend fun runGeneration(state: KatachiScreenState) {
        val rows = state.rows.filter { state.form.isSelected(it.id) }
        val items = rows.mapNotNull { generationItemOf(it, state.form) }
        ownWrites = items.flatMap { it.expectedPaths }.toSet()
        val session = GenerationSession(runner, fileSystem) { question ->
            val id = question.templateId
            mutableState.update { updateGenerationRow(it, id, GenerationRowStatus.AwaitingConflict).withConflict(question) }
            try {
                val expected = rows.firstOrNull { it.id == id }?.let { expectedContentsOf(it, state.form) }.orEmpty()
                effects.askConflict(question, expected)
            } finally {
                mutableState.update { updateGenerationRow(it, id, GenerationRowStatus.Running(null)).withConflict(null) }
            }
        }
        // From here on "cancel" reaches the session, also while saving and labelling (E-21).
        this.session = session
        effects.saveAllDocuments()
        val label = effects.putLocalHistoryLabel(rows.map { it.template.simpleName })
        generationLabel = label
        if (session.isCancelRequested) {
            // Cancelled before any build ran: back to the form, as if Generate had not been pressed.
            mutableState.update { it.copy(generation = null) }
            return
        }
        val listener = object : GenerationListener {
            private var current: TemplateId? = null

            override fun onItemStarted(index: Int) {
                val id = items[index].templateId
                current = id
                mutableState.update { updateGenerationRow(it, id, GenerationRowStatus.Running(null)) }
            }

            override fun onTask(taskPath: String) {
                val id = current ?: return
                mutableState.update { updateGenerationRow(it, id, GenerationRowStatus.Running(taskPath)) }
            }

            override fun onItemFinished(index: Int, result: GenerationItemResult) {
                mutableState.update { updateGenerationRow(it, items[index].templateId, GenerationRowStatus.Finished(result)) }
            }
        }
        val report = session.run(items, state.form.onExisting, listener)
        ownWrites = ownWrites + report.writtenFiles.map { it.path }
        try {
            effects.refreshFiles(report.writtenFiles.map { it.path })
            val toOpen = filesToOpen(report, effects.openAfterGeneration())
            if (toOpen.isNotEmpty()) effects.openFiles(toOpen)
        } finally {
            // The files are written whatever the IDE did with them: the result shows either way.
            mutableState.update { withExistingPaths(it.copy(generation = GenerationState.Finished(report, label))) }
        }
        effects.notifyGenerationFinished(report)
    }

    /**
     * [state] with the "already exists" badges of the checked rows' expected files (E-17). Checked
     * rows only, so typing stays cheap: a few `exists` calls per keystroke.
     */
    private fun withExistingPaths(state: KatachiScreenState): KatachiScreenState {
        val existing = state.rows.filter { state.form.isSelected(it.id) }.flatMap { row ->
            val detail = row.template.detail ?: return@flatMap emptyList()
            expectedFilesOf(detail, state.form.inputsOf(row.id))
                .mapNotNull { (it.location as? ExpectedLocation.Known)?.path }
                .filter { path -> resolveExpectedPath(row.module.linkedRootPath, path)?.let(fileSystem::exists) == true }
        }.toSet()
        return if (existing == state.view.existingPaths) state else state.copy(view = state.view.copy(existingPaths = existing))
    }

    private fun cancelGeneration() {
        if (mutableState.value.generation !is GenerationState.Running) return
        val active = session
        if (active != null) {
            active.cancel()
        } else {
            // Waiting for a load, or not at the session yet: nothing has run, so back to the form.
            generationJob?.cancel()
            mutableState.update { it.copy(generation = null) }
        }
    }

    private fun finishResult(next: (KatachiScreenState, GenerationReport) -> FormState) {
        val finished = mutableState.value.generation as? GenerationState.Finished ?: return
        mutableState.update { it.copy(form = next(it, finished.report), generation = null) }
    }

    private fun generationItemOf(row: ModuleTemplate, form: FormState): GenerationItem? {
        val detail = row.template.detail ?: return null
        val inputs = form.inputsOf(row.id)
        val expected = expectedFilesOf(detail, inputs).mapNotNull { (it.location as? ExpectedLocation.Known)?.path }
        return GenerationItem(
            templateId = row.id,
            module = row.module,
            args = templateArgsOf(row.template.roleName, detail, inputs, form.onExisting),
            expectedPaths = expected.mapNotNull { resolveExpectedPath(row.module.linkedRootPath, it) },
        )
    }

    private fun versions(): Map<ModuleId, String> = (detection as? DetectionResult.Found)?.katachiVersions.orEmpty()

    private fun emptyReasonOf(result: DetectionResult): EmptyReason = when (result) {
        DetectionResult.NotGradle -> EmptyReason.NotGradle
        DetectionResult.NotSynced -> EmptyReason.NotSynced
        DetectionResult.NotInstalled -> EmptyReason.NotInstalled
        is DetectionResult.Outdated -> EmptyReason.Outdated(result.katachiVersion)
        is DetectionResult.Found, is DetectionResult.TaskListMissing -> EmptyReason.NoTemplates
    }

    private companion object {
        val LOG = logger<KatachiToolWindowViewModel>()
    }
}

/** An unexpected exception as one line for the error details. */
private fun messageOf(e: Exception): String = listOfNotNull(e::class.simpleName, e.message).joinToString(": ")

private fun KatachiScreenState.withConflict(question: ConflictQuestion?): KatachiScreenState {
    val running = generation as? GenerationState.Running ?: return this
    return copy(generation = running.copy(conflict = question))
}
