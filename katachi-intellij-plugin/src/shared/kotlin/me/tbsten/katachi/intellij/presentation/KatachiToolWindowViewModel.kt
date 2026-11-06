package me.tbsten.katachi.intellij.presentation

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.detect.SyncedProjectSource
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.data.generate.GenerationListener
import me.tbsten.katachi.intellij.data.generate.GenerationSession
import me.tbsten.katachi.intellij.data.generate.filesToOpen
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.gradle.SerialGradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import me.tbsten.katachi.intellij.data.placement.placementRootOf
import me.tbsten.katachi.intellij.data.load.LoadResult
import me.tbsten.katachi.intellij.data.load.DefinitionLoader
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
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
import java.util.concurrent.ConcurrentHashMap

// dispatch is called on the EDT; work runs in the background, so every state change goes through `update`, and the synced data and cached JSON are read on ioDispatcher.
// Gradle runs one build at a time: a runner that is not a SerialGradleTaskRunner is wrapped in one.
internal class KatachiToolWindowViewModel(
    private val scope: CoroutineScope,
    private val syncedProject: SyncedProjectSource,
    runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
    private val effects: IdeEffects,
    initial: KatachiScreenState = KatachiScreenState(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loadsWithoutUser: () -> Boolean = { true },
    candidateLister: DirectoryLister = DirectoryLister { emptyList() },
    candidateSettle: suspend () -> Unit = { delay(CaptureCandidateLoader.SETTLE_MILLIS) },
) {
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<KatachiScreenState> = mutableState.asStateFlow()

    private val runner: GradleTaskRunner = runner as? SerialGradleTaskRunner ?: SerialGradleTaskRunner(runner)
    private val loader = DefinitionLoader(this.runner, fileSystem)

    // Written on the EDT or in the background, read on the other.
    @Volatile private var detection: DetectionResult? = null

    @Volatile private var loadJob: Job? = null

    @Volatile private var generationJob: Job? = null

    @Volatile private var session: GenerationSession? = null

    @Volatile private var generationLabel: String? = null

    @Volatile private var ownWrites: Set<Path> = emptySet()

    private val entryOwnWrites: MutableSet<Path> = ConcurrentHashMap.newKeySet()

    private var opened = false

    private var detectedForEntries = false

    private val mutableLoadStarted = MutableStateFlow(false)

    val loadStarted: StateFlow<Boolean> = mutableLoadStarted.asStateFlow()

    val isLoadRunning: Boolean get() = loadJob?.isActive == true

    val isGenerating: Boolean get() = mutableState.value.generation is GenerationState.Running

    /** Returns once the detection or load running now (if any) has ended. */
    suspend fun awaitLoad() {
        loadJob?.join()
    }

    private val candidateLoader = CaptureCandidateLoader(
        scope = scope,
        lister = candidateLister,
        ioContext = ioDispatcher,
        settle = candidateSettle,
        onFailure = { LOG.warn("Reading the directories for the capture candidates failed", it) },
        onLoaded = { field, list ->
            mutableState.update { state ->
                val next = if (list == null) state.captureCandidates - field else state.captureCandidates + (field to list)
                if (next == state.captureCandidates) state else state.copy(captureCandidates = next)
            }
        },
    )

    private val requestedFields = HashSet<FieldId>()

    private val candidateRoots = ConcurrentHashMap<ModuleId, Path>()

    private val ideIntents = IdeIntentHandler(scope, this.runner, fileSystem, effects, { mutableState.value }, { mutableState.update(it) })

    val definitionModules: List<KatachiModule> get() = mutableState.value.modules

    fun isOwnWrite(path: Path): Boolean = path in ownWrites || path in entryOwnWrites

    fun registerOwnWrite(path: Path) {
        entryOwnWrites.add(path)
    }

    fun dispatch(intent: KatachiIntent) {
        dispatchIntent(intent)
        syncCandidates()
    }

    fun onFilesChanged() {
        candidateRoots.clear()
        syncCandidates(force = true)
    }

    private fun syncCandidates(force: Boolean = false) {
        // Called from the EDT (dispatch), the background (a load or a generation ended) and the VFS listener. The state is
        // read inside the lock, so a caller that got the lock later always works from a newer state than the one before it.
        synchronized(candidateLoader.lock) {
            val state = mutableState.value
            val wanted = state.rows
                .filter { state.form.isSelected(it.id) && it.id in state.form.expanded && it.template.unavailability == null }
                .flatMap { row -> candidateRequestsOf(row, state.form.inputsOf(row.id), candidateRoots.getOrPut(row.module.id) { placementRootOf(row.module, fileSystem) }) }
            val wantedFields = wanted.map { it.field }.toSet()
            for (field in requestedFields - wantedFields) candidateLoader.forget(field)
            requestedFields.clear()
            requestedFields.addAll(wantedFields)
            for (request in wanted) candidateLoader.request(request, force)
            if (state.captureCandidates.keys.any { it !in wantedFields }) {
                mutableState.update { current -> current.copy(captureCandidates = current.captureCandidates.filterKeys { it in wantedFields }) }
            }
        }
    }

    private fun dispatchIntent(intent: KatachiIntent) {
        var applied = false
        // update, not read-then-write: a load or a generation may be updating the state meanwhile.
        mutableState.update { current ->
            val next = applyFormIntent(current, intent)
            applied = next != null
            next ?: current
        }
        if (applied) return
        if (ideIntents.handle(intent)) return
        when (intent) {
            KatachiIntent.Opened -> if (!opened) {
                opened = true
                // An entry may have loaded the list already: the tool window shows it as it is (issue 11).
                startLoad()
            }
            KatachiIntent.EnsureLoaded -> ensureLoaded()
            // Only after a load: a sync alone never runs Gradle for someone who does not look (spec 04, decision 16).
            KatachiIntent.SyncCompleted -> when {
                mutableLoadStarted.value -> if (mutableState.value.phase !is ScreenPhase.Ready) detectAndLoad() else redetectAfterSync()
                detectedForEntries -> detectOnly()
            }
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
        reloadIfIdle()
    }

    // Returns false while a detection, a load or a generation is still going; the automatic reload waits for it and asks again. On the EDT.
    fun reloadIfIdle(): Boolean {
        if (mutableState.value.generation is GenerationState.Running || loadJob?.isActive == true) return false
        mutableLoadStarted.value = true
        launchLoad(detect = true)
        return true
    }

    private fun startLoad() {
        if (mutableLoadStarted.value) return
        mutableLoadStarted.value = true
        launchLoad(detect = true, after = loadJob)
    }

    private fun ensureLoaded() {
        if (mutableLoadStarted.value) return
        if (loadsWithoutUser()) {
            startLoad()
        } else if (!detectedForEntries) {
            detectedForEntries = true
            detectOnly()
        }
    }

    private fun detectOnly() {
        if (loadJob?.isActive == true) return
        loadJob = scope.launch {
            try {
                detect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Reading the katachi definition modules failed unexpectedly", e)
            }
        }
    }

    suspend fun reloadForGeneration(module: KatachiModule): LoadResult {
        loadJob?.join()
        mutableLoadStarted.value = true
        effects.saveAllDocuments()
        val result = loader.load(listOf(module), versions(), object : GradleRunListener {})
        if (result is LoadResult.Loaded) {
            mutableState.update { state ->
                val others = state.snapshots.filter { snapshot -> result.snapshots.none { it.module.id == snapshot.module.id } }
                val modules = if (state.modules.any { it.id == module.id }) state.modules else state.modules + module
                applyLoaded(state.copy(modules = modules), (others + result.snapshots).sortedBy { snapshot -> modules.indexOfFirst { it.id == snapshot.module.id } })
            }
            candidateRoots.clear()
            syncCandidates(force = true)
        }
        return result
    }

    private fun redetectAfterSync() {
        if (mutableState.value.generation is GenerationState.Running || loadJob?.isActive == true) return
        launchLoad(detect = true, onlyWhenModulesChanged = true)
    }

    private fun detectAndLoad() {
        if (loadJob?.isActive == true) return
        launchLoad(detect = true)
    }

    private fun launchLoad(detect: Boolean, onlyWhenModulesChanged: Boolean = false, after: Job? = null) {
        loadJob = scope.launch {
            try {
                after?.join()
                if (detect && !detect(onlyWhenModulesChanged)) return@launch
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

    private suspend fun detect(onlyWhenModulesChanged: Boolean = false): Boolean {
        mutableState.update { it.copy(phase = if (it.snapshots.isEmpty()) ScreenPhase.Initializing else it.phase) }
        // Walking the synced data and parsing the cached JSON are too slow for the EDT (a large Android build).
        val (detected, cached) = withContext(ioDispatcher) {
            val found = detectDefinitionModules(syncedProject.read())
            found to if (found is DetectionResult.Found) loader.readCached(found.modules, found.katachiVersions) else emptyList()
        }
        val result = withKnownCapabilities(detected, mutableState.value.modules)
        val modules = when (result) {
            is DetectionResult.Found -> result.modules
            is DetectionResult.TaskListMissing -> result.candidates
            else -> null
        }
        val unchanged = modules == mutableState.value.modules && mutableState.value.snapshots.isNotEmpty()
        if (onlyWhenModulesChanged && unchanged) return false
        detection = result
        if (modules == null) {
            // No list to show a result on: it goes, as when a load leaves no template (applyLoaded).
            mutableState.update {
                it.copy(phase = ScreenPhase.Empty(emptyReasonOf(result)), modules = emptyList(), generation = it.generation as? GenerationState.Running)
            }
            return false
        }
        // The list on screen is newer than the cache when the modules did not change.
        if (!unchanged) {
            mutableState.update { state ->
                val snapshots = provisionalSnapshotsOf(modules, state.snapshots, cached)
                if (snapshots.isEmpty()) state.copy(modules = modules) else applyLoaded(state.copy(modules = modules), snapshots)
            }
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
        val found = detection
        val result = when (found) {
            is DetectionResult.TaskListMissing -> loader.loadCandidates(found.candidates, versions(), listener)
            else -> loader.load(modules, versions(), listener)
        }
        // A candidate that answered a task has it: the next load runs that task only, and 0.2.x is told from 0.3.
        val answered = if (found is DetectionResult.TaskListMissing && result is LoadResult.Loaded) result.snapshots.associate { it.module.id to it.module } else emptyMap()
        if (found is DetectionResult.TaskListMissing && answered.isNotEmpty()) {
            detection = DetectionResult.TaskListMissing(found.candidates.map { answered[it.id] ?: it })
        }
        mutableState.update { state ->
            when (result) {
                is LoadResult.Loaded -> applyLoaded(state.copy(modules = state.modules.map { answered[it.id] ?: it }), result.snapshots)
                is LoadResult.Failed -> applyLoadFailed(state, result.failure, result.output)
            }
        }
        if (result is LoadResult.Failed) effects.notifyLoadFailed()
        // The definition was read again: what the candidates were read from may have changed with it.
        candidateRoots.clear()
        syncCandidates(force = result is LoadResult.Loaded)
    }

    private fun generate() {
        val current = mutableState.value
        if (current.generation != null) return
        // A background refresh over a cache does not block (E-41): generation waits for it instead.
        if (current.generateBlocker != null) return
        val refreshing = loadJob?.isActive == true
        generationLabel = null
        mutableState.update { if (it.generation == null) startGeneration(it, waitingForLoad = refreshing) else it }
        // This job's own session: its cleanup must not clear the session of a generation started
        // after this one was cancelled (Generate pressed again right after cancelling).
        val session = newSession()
        generationJob = scope.launch {
            try {
                loadJob?.join()
                val state = mutableState.value
                if (generateBlockerOf(state.rows, state.form, BusyState.Idle) != null) {
                    // The refreshed list no longer allows it (a template or a parameter changed).
                    mutableState.update { it.copy(generation = null) }
                    return@launch
                }
                runGeneration(state, session)
            } catch (e: CancellationException) {
                // cancelGeneration() already left the running state, or the project is closing.
                throw e
            } catch (e: Exception) {
                LOG.warn("Generating from katachi templates failed unexpectedly", e)
                val failure = GenerationFailure.NotReached(GradleFailure.Other(listOf(messageOf(e))))
                mutableState.update { abortGeneration(it, failure, generationLabel) }
            } finally {
                if (this@KatachiToolWindowViewModel.session === session) this@KatachiToolWindowViewModel.session = null
            }
        }
    }

    private fun newSession(): GenerationSession = GenerationSession(runner, fileSystem) { question ->
        val ids = question.templateIds
        mutableState.update { updateGenerationRow(it, ids, GenerationRowStatus.AwaitingConflict).withConflict(question) }
        try {
            effects.askConflict(question)
        } finally {
            mutableState.update { updateGenerationRow(it, ids, GenerationRowStatus.Running(null)).withConflict(null) }
        }
    }

    private suspend fun runGeneration(state: KatachiScreenState, session: GenerationSession) {
        val rows = state.rows.filter { state.form.isSelected(it.id) }
        val items = generationItemsOf(rows, state.form)
        ownWrites = items.flatMap { it.expectedPaths }.toSet()
        // From here on "cancel" reaches the session, also while saving and labelling (E-21).
        this.session = session
        effects.saveAllDocuments()
        val label = effects.putLocalHistoryLabel(rows.map { it.template.title })
        generationLabel = label
        if (session.isCancelRequested) {
            // Cancelled before any build ran: back to the form, as if Generate had not been pressed.
            mutableState.update { it.copy(generation = null) }
            return
        }
        val listener = object : GenerationListener {
            private var current: List<TemplateId> = emptyList()

            override fun onItemStarted(index: Int) {
                val ids = items[index].templateIds
                current = ids
                mutableState.update { updateGenerationRow(it, ids, GenerationRowStatus.Running(null)) }
            }

            override fun onTask(taskPath: String) {
                if (current.isEmpty()) return
                mutableState.update { updateGenerationRow(it, current, GenerationRowStatus.Running(taskPath)) }
            }

            override fun onItemFinished(index: Int, result: GenerationItemResult) {
                mutableState.update { updateGenerationRow(it, items[index].templateIds, GenerationRowStatus.Finished(result)) }
            }
        }
        val report = session.run(items, state.form.onExisting, listener)
        ownWrites = ownWrites + report.writtenFiles.map { it.path }
        var opened = emptyList<Path>()
        try {
            effects.refreshFiles(report.writtenFiles.map { it.path })
            val toOpen = filesToOpen(report, effects.openAfterGeneration())
            if (toOpen.isNotEmpty()) opened = effects.openFiles(toOpen)
        } finally {
            // The files are written whatever the IDE did with them: the result shows either way.
            mutableState.update { it.copy(generation = GenerationState.Finished(report, label, opened)) }
        }
        effects.notifyGenerationFinished(report)
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
        mutableState.update { leaveResult(it, next(it, finished.report)) }
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

// The cache may hold the answer of a load that was stopped, which the screen never took: showing it for a module on screen would drop checks (E-45).
private fun provisionalSnapshotsOf(
    modules: List<KatachiModule>,
    shown: List<DescriptionSnapshot>,
    cached: List<DescriptionSnapshot>,
): List<DescriptionSnapshot> = modules.mapNotNull { module ->
    shown.firstOrNull { it.module.id == module.id }?.copy(module = module) ?: cached.firstOrNull { it.module.id == module.id }
}

private fun withKnownCapabilities(detected: DetectionResult, known: List<KatachiModule>): DetectionResult {
    if (detected !is DetectionResult.TaskListMissing) return detected
    return DetectionResult.TaskListMissing(
        detected.candidates.map { candidate ->
            val capabilities = known.firstOrNull { it.id == candidate.id }?.capabilities.orEmpty()
            if (capabilities.isEmpty()) candidate else candidate.copy(capabilities = capabilities)
        },
    )
}

private fun messageOf(e: Exception): String = listOfNotNull(e::class.simpleName, e.message).joinToString(": ")

private fun KatachiScreenState.withConflict(question: ConflictQuestion?): KatachiScreenState {
    val running = generation as? GenerationState.Running ?: return this
    return copy(generation = running.copy(conflict = question))
}
