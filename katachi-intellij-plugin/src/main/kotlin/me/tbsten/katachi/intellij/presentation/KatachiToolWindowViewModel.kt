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
            next ?: current
        }
        if (applied) return
        if (ideIntents.handle(intent)) return
        when (intent) {
            KatachiIntent.Opened -> if (!opened) {
                opened = true
                detectAndLoad()
            }
            // Only after the tool window was shown: a sync alone never runs Gradle for someone who does not look (spec 04).
            KatachiIntent.SyncCompleted -> if (opened) {
                if (mutableState.value.phase !is ScreenPhase.Ready) detectAndLoad() else redetectAfterSync()
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
        launchLoad(detect = true)
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
    private fun launchLoad(detect: Boolean, onlyWhenModulesChanged: Boolean = false) {
        loadJob = scope.launch {
            try {
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
                if (cached.isEmpty()) state.copy(modules = modules) else applyLoaded(state.copy(modules = modules), cached)
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

/** An unexpected exception as one line for the error details. */
private fun messageOf(e: Exception): String = listOfNotNull(e::class.simpleName, e.message).joinToString(": ")

private fun KatachiScreenState.withConflict(question: ConflictQuestion?): KatachiScreenState {
    val running = generation as? GenerationState.Running ?: return this
    return copy(generation = running.copy(conflict = question))
}
