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
import me.tbsten.katachi.intellij.data.generate.TemplateArgsEntry
import me.tbsten.katachi.intellij.data.generate.filesToOpen
import me.tbsten.katachi.intellij.data.generate.templateArgsOf
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.gradle.SerialGradleTaskRunner
import me.tbsten.katachi.intellij.data.load.LoadResult
import me.tbsten.katachi.intellij.data.load.TemplateDescriptionLoader
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

/**
 * Holds the tool window's [state] and carries out [KatachiIntent]s: pure ones through
 * [applyFormIntent], the rest through the ports (synced data, Gradle, disk, IDE).
 *
 * All work runs in [scope], which the project service owns: closing the project cancels it, and
 * with it the running Gradle build (E-49). [dispatch] is called on the EDT; the work it starts runs
 * in the background, so every state change goes through `update`, and the synced data and the cached
 * JSON are read on [ioDispatcher]. Gradle runs one build at a time ([SerialGradleTaskRunner]): the
 * project service hands in its one serial runner, shared with the entries (issue 11); any other
 * runner is wrapped in one here.
 *
 * The list is loaded once per session, by whichever asks first: the tool window ([KatachiIntent.Opened])
 * or the New menu and the editor notification ([KatachiIntent.EnsureLoaded], issues 11, 18).
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
    /**
     * Whether [KatachiIntent.EnsureLoaded] may run Gradle (the setting "load without a user action",
     * decision 16); read at each one. When not, it only detects and shows the cached JSON.
     */
    private val loadsWithoutUser: () -> Boolean = { true },
) {
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<KatachiScreenState> = mutableState.asStateFlow()

    private val runner: GradleTaskRunner = runner as? SerialGradleTaskRunner ?: SerialGradleTaskRunner(runner)
    private val loader = TemplateDescriptionLoader(this.runner, fileSystem)

    // Written on the EDT or in the background, read on the other.
    @Volatile private var detection: DetectionResult? = null

    @Volatile private var loadJob: Job? = null

    @Volatile private var generationJob: Job? = null

    @Volatile private var session: GenerationSession? = null

    @Volatile private var generationLabel: String? = null

    /** The files the latest generation writes or may write, absolute (E-44: not a definition change). */
    @Volatile private var ownWrites: Set<Path> = emptySet()

    /** The files an entry's generation writes (E3), kept for the session: never a definition change. */
    private val entryOwnWrites: MutableSet<Path> = ConcurrentHashMap.newKeySet()

    private var opened = false

    /** EnsureLoaded ran with loading turned off: the synced data was read, and is read again after a sync. */
    private var detectedForEntries = false

    private val mutableLoadStarted = MutableStateFlow(false)

    /**
     * Whether a load of the list started in this session, from the tool window, an entry, ⟳ or a
     * generation. Only then does a sync or a definition change load again (decision 16).
     */
    val loadStarted: StateFlow<Boolean> = mutableLoadStarted.asStateFlow()

    /** Whether a detection or a load is running or queued; tests wait for it to end. */
    val isLoadRunning: Boolean get() = loadJob?.isActive == true

    private val ideIntents = IdeIntentHandler(scope, this.runner, fileSystem, effects, { mutableState.value }, { mutableState.update(it) })

    /** The definition modules found by the latest detection, for watching their files (E-44). */
    val definitionModules: List<KatachiModule> get() = mutableState.value.modules

    /** Whether [path] is a file the latest generation writes, whose VFS event is not a definition change. */
    fun isOwnWrite(path: Path): Boolean = path in ownWrites || path in entryOwnWrites

    /** [path] is about to be written by an entry's generation: not a definition change (E-44). */
    fun registerOwnWrite(path: Path) {
        entryOwnWrites.add(path)
    }

    fun dispatch(intent: KatachiIntent) {
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

    /** ⟳ reads the synced data again first (no Gradle), so a module added since shows too. */
    private fun reload() {
        val current = mutableState.value
        if (current.generation is GenerationState.Running || loadJob?.isActive == true) return
        mutableLoadStarted.value = true
        launchLoad(detect = true)
    }

    /** The first load of the session; after a detection of [detectOnly] that may still run. */
    private fun startLoad() {
        if (mutableLoadStarted.value) return
        mutableLoadStarted.value = true
        launchLoad(detect = true, after = loadJob)
    }

    /**
     * An entry needs the list (issues 11, 18): the first load, unless one started already. With
     * loading turned off, only the synced data and the cached JSON are read (decision 16).
     */
    private fun ensureLoaded() {
        if (mutableLoadStarted.value) return
        if (loadsWithoutUser()) {
            startLoad()
        } else if (!detectedForEntries) {
            detectedForEntries = true
            detectOnly()
        }
    }

    /** Reads the synced data and shows the cached list, without Gradle. */
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

    /**
     * Re-reads [module]'s templates for a generation from an entry and puts them in [state] before
     * returning (decision 1), after the load that is running, if any. The other modules' lists stay.
     * A failure leaves the list as it was: the generation reports it.
     */
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
        }
        return result
    }

    /**
     * A sync over a shown list: the definition modules are looked for again, and Gradle runs only
     * when they changed (a module added or removed). A sync that changed nothing leaves the list as it is.
     */
    private fun redetectAfterSync() {
        if (mutableState.value.generation is GenerationState.Running || loadJob?.isActive == true) return
        launchLoad(detect = true, onlyWhenModulesChanged = true)
    }

    private fun detectAndLoad() {
        if (loadJob?.isActive == true) return
        launchLoad(detect = true)
    }

    /** Detects first when [detect], then loads. Whatever goes wrong, `loading` does not stay set. */
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

    /**
     * Reads the synced data and shows the cached list; `false` when there is nothing to load, or when
     * [onlyWhenModulesChanged] and the definition modules are the ones already shown.
     */
    private suspend fun detect(onlyWhenModulesChanged: Boolean = false): Boolean {
        mutableState.update { it.copy(phase = if (it.snapshots.isEmpty()) ScreenPhase.Initializing else it.phase) }
        // Walking the synced data and parsing the cached JSON are too slow for the EDT (a large Android build).
        val (result, cached) = withContext(ioDispatcher) {
            val found = detectDefinitionModules(syncedProject.read())
            found to if (found is DetectionResult.Found) loader.readCached(found.modules, found.katachiVersions) else emptyList()
        }
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
        val result = when (val found = detection) {
            is DetectionResult.TaskListMissing -> loader.loadCandidates(found.candidates, versions(), listener)
            else -> loader.load(modules, versions(), listener)
        }
        mutableState.update { state ->
            when (result) {
                is LoadResult.Loaded -> applyLoaded(state, result.snapshots)
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

    /** A session that marks every row of the asking run waiting for the conflict dialog while it asks. */
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

    /**
     * [rows] as the runs a generation makes: one [GenerationItem] per module, covering every
     * checked row of that module in one `katachiTemplate` build (design draft section 6 "IDE の複数選択").
     * A row whose preview failed is left out (it cannot be checked in the first place).
     */
    private fun generationItemsOf(rows: List<ModuleTemplate>, form: FormState): List<GenerationItem> =
        rows.groupBy { it.module.id }.values.mapNotNull { group ->
            val checked = group.mapNotNull { row -> row.template.detail?.let { row to it } }
            if (checked.isEmpty()) return@mapNotNull null
            val entries = checked.map { (row, detail) -> TemplateArgsEntry(row.template.template, detail, form.inputsOf(row.id)) }
            val expected = checked.flatMap { (row, detail) ->
                expectedFilesOf(detail, form.inputsOf(row.id)).mapNotNull { (it.location as? ExpectedLocation.Known)?.path }
                    .mapNotNull { resolveExpectedPath(row.module.linkedRootPath, it) }
            }
            GenerationItem(
                templateIds = checked.map { (row, _) -> row.id },
                module = checked.first().first.module,
                args = templateArgsOf(entries, form.onExisting),
                expectedPaths = expected,
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

/**
 * What a detection shows of [modules] until the load that follows it answers: a module already on
 * screen keeps the list on screen, and only a module not shown yet takes its [cached] JSON (E-43).
 * The cache may hold the answer of a load that was stopped, which the screen never took; showing it
 * for a module on screen would drop checks (E-45) that the load then brings back unchecked.
 */
private fun provisionalSnapshotsOf(
    modules: List<KatachiModule>,
    shown: List<DescriptionSnapshot>,
    cached: List<DescriptionSnapshot>,
): List<DescriptionSnapshot> = modules.mapNotNull { module ->
    shown.firstOrNull { it.module.id == module.id }?.copy(module = module) ?: cached.firstOrNull { it.module.id == module.id }
}

/** An unexpected exception as one line for the error details. */
private fun messageOf(e: Exception): String = listOfNotNull(e::class.simpleName, e.message).joinToString(": ")

private fun KatachiScreenState.withConflict(question: ConflictQuestion?): KatachiScreenState {
    val running = generation as? GenerationState.Running ?: return this
    return copy(generation = running.copy(conflict = question))
}
