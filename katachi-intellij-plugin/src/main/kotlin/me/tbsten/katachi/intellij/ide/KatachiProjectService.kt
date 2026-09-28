package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.detect.SyncedProjectSource
import me.tbsten.katachi.intellij.data.generate.GenerationCatalogPort
import me.tbsten.katachi.intellij.data.generate.GenerationCatalogReload
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.gradle.SerialGradleTaskRunner
import me.tbsten.katachi.intellij.data.load.LoadResult
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.data.placement.placementRootOf
import me.tbsten.katachi.intellij.ide.entry.EntryEffectsImpl
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.FormState
import me.tbsten.katachi.intellij.presentation.IdeEffects
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel
import me.tbsten.katachi.intellij.presentation.PlacementIndexSource
import me.tbsten.katachi.intellij.presentation.ViewState
import me.tbsten.katachi.intellij.presentation.entry.EntryEffects
import me.tbsten.katachi.intellij.presentation.entry.EntryGenerationLedger
import me.tbsten.katachi.intellij.presentation.placementIndexStateOf
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The ports the ViewModel runs on. Tests swap the synced data and Gradle for fakes. */
internal data class KatachiPorts(
    val syncedProject: SyncedProjectSource,
    val runner: GradleTaskRunner,
    val fileSystem: ProjectFileSystem,
    val effects: IdeEffects,
    /** `null`: the real [EntryEffectsImpl]. Tests of the notification and the New menu pass a fake. */
    val entryEffects: EntryEffects? = null,
    /** `null`: whether the platform is importing a Gradle project now. Tests play an import with it. */
    val importInProgress: (() -> Boolean)? = null,
    /** `null`: `EditorNotifications.updateAllNotifications()`. Tests record the calls. */
    val refreshNotifications: (() -> Unit)? = null,
    /** `null`: [TemplatePlacementIndex.build] over [placementRootOf]. Tests wrap it to see where it runs. */
    val buildIndex: ((List<DescriptionSnapshot>) -> TemplatePlacementIndex)? = null,
) {
    companion object {
        fun of(project: Project): KatachiPorts = KatachiPorts(
            syncedProject = ProjectDataModuleSource(project),
            runner = ExternalSystemGradleTaskRunner(project),
            fileSystem = NioProjectFileSystem,
            effects = IdeEffectsImpl(project),
        )
    }
}

/**
 * Owns the tool window's ViewModel for one project, and with it everything that runs: loads,
 * generations and listeners live in [scope], which the platform cancels when the project closes,
 * so a running Gradle build is cancelled too (E-49).
 *
 * Created the first time the tool window is shown, so a project nobody looks at never runs Gradle.
 * The New menu and the editor notification create it too, but only when the synced data has a
 * definition module (issue 11, decision 17); they share its list through [placementIndex] and start
 * the load with [ensureLoaded]. Syncs and definition changes are followed whoever created it.
 */
@OptIn(ExperimentalCoroutinesApi::class) // mapLatest: a newer list cancels the index still being built.
@Service(Service.Level.PROJECT)
internal class KatachiProjectService(
    private val project: Project,
    val scope: CoroutineScope,
    ports: KatachiPorts,
) : Disposable, GenerationCatalogPort {
    @Suppress("unused") // Called by the platform.
    constructor(project: Project, scope: CoroutineScope) : this(project, scope, KatachiPorts.of(project))

    private val settings: KatachiSettings? = sdkCall("read the katachi settings") { KatachiSettings.getInstance(project) }.getOrNull()

    /**
     * The project's one Gradle runner: the tool window, the entries and the generation from an entry
     * all run through it, one build at a time, so they never fight over the same `build/`.
     */
    val gradleRunner: GradleTaskRunner = SerialGradleTaskRunner(ports.runner)

    private val fileSystem: ProjectFileSystem = ports.fileSystem
    private val importInProgress: () -> Boolean = ports.importInProgress ?: ::platformImportInProgress
    private val refreshNotifications: () -> Unit = ports.refreshNotifications ?: ::updateAllNotifications
    private val buildIndex: (List<DescriptionSnapshot>) -> TemplatePlacementIndex =
        ports.buildIndex ?: { snapshots -> TemplatePlacementIndex.build(snapshots) { placementRootOf(it, fileSystem) } }

    /** Imports running now, as the listener saw them start and end. */
    private val importsRunning = AtomicInteger(0)

    /** The generations started from the entries (E3 writes, E1 reads). Memory only. */
    val ledger: EntryGenerationLedger = EntryGenerationLedger()

    /** What the notification's links and the New menu's items ask of the IDE. */
    val entryEffects: EntryEffects = ports.entryEffects ?: EntryEffectsImpl(project)

    private val mutablePlacementIndex = MutableStateFlow<PlacementIndexState>(PlacementIndexState.NotLoaded)

    /**
     * The placement index of the loaded list, built off the EDT from [viewModel]'s state, the one
     * source of the list (issues 4, 11). Each change updates the editor notifications.
     */
    val placementIndex: StateFlow<PlacementIndexState> = mutablePlacementIndex.asStateFlow()

    val viewModel: KatachiToolWindowViewModel = KatachiToolWindowViewModel(
        scope = scope,
        syncedProject = ports.syncedProject,
        runner = gradleRunner,
        fileSystem = ports.fileSystem,
        effects = ports.effects,
        initial = KatachiScreenState(
            form = settings?.let { FormState(onExisting = it.onExisting) } ?: FormState(),
            view = settings?.let { ViewState(collapsedModules = it.collapsedModules) } ?: ViewState(),
        ),
        loadsWithoutUser = { settings?.let { sdkCall("read the auto-load setting") { it.autoLoadTemplates }.getOrNull() } ?: true },
    )

    init {
        // Without it the list does not follow a Gradle sync; ⟳ still reloads it.
        sdkCall("listen to Gradle syncs") {
            project.messageBus.connect(this).subscribe(
                ProjectDataImportListener.TOPIC,
                object : ProjectDataImportListener {
                    override fun onImportStarted(projectPath: String?) {
                        importsRunning.incrementAndGet()
                    }

                    override fun onImportFinished(projectPath: String?) {
                        importEnded()
                        dispatchOnEdt(KatachiIntent.SyncCompleted)
                        if (ensureAfterImport) ensureLoaded()
                    }

                    override fun onImportFailed(projectPath: String?, t: Throwable) {
                        importEnded()
                        if (ensureAfterImport) ensureLoaded()
                    }
                },
            )
        }
        // Off the EDT: the index walks every template (thread placement in plan chapter 1).
        scope.launch {
            combine(viewModel.state, viewModel.loadStarted) { state, started -> PlacementIndexSource.of(state, started) }
                .distinctUntilChanged()
                .mapLatest { source -> placementIndexStateOf(source, ::indexOf) }
                .flowOn(Dispatchers.Default)
                .collect(::publishIndex)
        }
        // What the user picks in the combo and folds stays with the project (spec 04 "入力値の保存").
        // On the EDT, like the settings UI, so that two writes of the set never overlap.
        if (settings != null) {
            scope.launch(Dispatchers.EDT) {
                viewModel.state.map { it.form.onExisting to it.view.collapsedModules }.distinctUntilChanged().collect { (onExisting, collapsed) ->
                    sdkCall("save the katachi settings") {
                        settings.onExisting = onExisting
                        settings.collapsedModules = collapsed
                    }
                }
            }
        }
    }

    private var autoReload: Job? = null

    /**
     * A definition file changed (E-44): the banner, and with "reload when saved" a reload two seconds
     * after the last change. Saves made by a running load are its own and schedule nothing.
     */
    fun onDefinitionChanged() {
        dispatchOnEdt(KatachiIntent.DefinitionChanged)
        // Nothing loaded in this session (loading without the user turned off): a change does not start one.
        if (settings?.autoReloadOnSave != true || viewModel.state.value.loading != null || !viewModel.loadStarted.value) return
        autoReload?.cancel()
        autoReload = scope.launch {
            delay(AUTO_RELOAD_DELAY_MILLIS)
            withContext(Dispatchers.EDT) { viewModel.dispatch(KatachiIntent.Reload) }
        }
    }

    private val ensureScheduled = AtomicBoolean(false)

    /** An entry asked during a Gradle import: it runs once the import ends (decision 16). */
    @Volatile private var ensureAfterImport = false

    /**
     * Starts detecting and loading the list if nothing did yet (issues 11, 18). Callable from any
     * thread any number of times (a read action that restarts calls it again): it hops to the EDT
     * and loads once, and never waits. During a Gradle import it waits for the import to end.
     * With loading without the user turned off it only reads the synced data and the cached JSON.
     */
    fun ensureLoaded() {
        if (viewModel.loadStarted.value) return
        // Many calls while one hop is queued make that one hop.
        if (!ensureScheduled.compareAndSet(false, true)) return
        scope.launch(Dispatchers.EDT) {
            ensureScheduled.set(false)
            if (importing()) {
                ensureAfterImport = true
                return@launch
            }
            ensureAfterImport = false
            viewModel.dispatch(KatachiIntent.EnsureLoaded)
        }
    }

    /** [path] is about to be written by this plugin: not a definition change (E-44). */
    override fun registerOwnWrite(path: Path) = viewModel.registerOwnWrite(path)

    /**
     * Re-reads [module]'s templates for a generation through [gradleRunner] and publishes them to
     * the tool window and [placementIndex] before returning (decision 1). Waits for a running load.
     */
    override suspend fun reloadForGeneration(module: KatachiModule): GenerationCatalogReload =
        when (val result = viewModel.reloadForGeneration(module)) {
            is LoadResult.Failed -> GenerationCatalogReload.Failed(result.failure)
            is LoadResult.Loaded -> {
                val snapshots = viewModel.state.value.snapshots
                val index = withContext(Dispatchers.Default) { indexOf(snapshots) }
                // Now, not when the collector gets to it: the caller resolves its target against this index.
                publishIndex(PlacementIndexState.Ready(index))
                GenerationCatalogReload.Reloaded(snapshots, index)
            }
        }

    /**
     * The latest index and the list it was built from, so one list is indexed (and notified) once,
     * even when the collector and [reloadForGeneration] ask for it at the same moment.
     */
    private var lastIndex: Pair<List<DescriptionSnapshot>, TemplatePlacementIndex>? = null
    private val indexLock = Any()

    private fun indexOf(snapshots: List<DescriptionSnapshot>): TemplatePlacementIndex = synchronized(indexLock) {
        lastIndex?.let { (from, index) -> if (from === snapshots) return index }
        buildIndex(snapshots).also { lastIndex = snapshots to it }
    }

    private fun publishIndex(state: PlacementIndexState) {
        if (mutablePlacementIndex.value == state) return
        mutablePlacementIndex.value = state
        sdkCall("update the editor notifications") { refreshNotifications() }
    }

    private fun importing(): Boolean = importsRunning.get() > 0 || importInProgress()

    private fun importEnded() {
        importsRunning.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    private fun platformImportInProgress(): Boolean = sdkCall("ask whether Gradle is importing") {
        ExternalSystemProcessingManager.getInstance().hasTaskOfTypeInProgress(ExternalSystemTaskType.RESOLVE_PROJECT, project)
    }.getOrNull() == true

    private fun updateAllNotifications() {
        if (!project.isDisposed) EditorNotifications.getInstance(project).updateAllNotifications()
    }

    /** The ViewModel is driven from the EDT only; IDE listeners call from anywhere. */
    private fun dispatchOnEdt(intent: KatachiIntent) {
        scope.launch(Dispatchers.EDT) { viewModel.dispatch(intent) }
    }

    override fun dispose() = Unit

    companion object {
        private const val AUTO_RELOAD_DELAY_MILLIS = 2_000L

        /** Call inside [sdkCall]. */
        fun getInstance(project: Project): KatachiProjectService = project.service()
    }
}
