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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.data.generate.GenerationCatalogPort
import me.tbsten.katachi.intellij.data.generate.GenerationCatalogReload
import me.tbsten.katachi.intellij.data.generate.SingleFileGeneration
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationResult
import me.tbsten.katachi.intellij.data.gradle.GradleActivity
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.gradle.SerialGradleTaskRunner
import me.tbsten.katachi.intellij.data.load.LoadResult
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.data.placement.placementRootOf
import me.tbsten.katachi.intellij.data.watch.FileChanges
import me.tbsten.katachi.intellij.data.watch.WatchedModule
import me.tbsten.katachi.intellij.ide.definition.DeclarationSiteResolver
import me.tbsten.katachi.intellij.ide.definition.DefinitionStatePublisher
import me.tbsten.katachi.intellij.ide.definition.RefreshReason
import me.tbsten.katachi.intellij.ide.entry.EntryEffectsImpl
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.answer
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

internal data class KatachiPorts(
    val syncedProject: SyncedProjectSource,
    val runner: GradleTaskRunner,
    val fileSystem: ProjectFileSystem,
    val effects: IdeEffects,
    val entryEffects: EntryEffects? = null,
    val importInProgress: (() -> Boolean)? = null,
    val refreshNotifications: (() -> Unit)? = null,
    val buildIndex: ((List<DescriptionSnapshot>) -> TemplatePlacementIndex)? = null,
    val gradleActivity: GradleActivity? = null,
    val autoReloadDelayMillis: Long = AutoReload.AUTO_RELOAD_DELAY_MILLIS,
    val ownSave: OwnSaveMark = OwnSaveMark(),
) {
    companion object {
        fun of(project: Project): KatachiPorts {
            val ownSave = OwnSaveMark()
            return KatachiPorts(
                syncedProject = ProjectDataModuleSource(project),
                runner = ExternalSystemGradleTaskRunner(project),
                fileSystem = NioProjectFileSystem,
                effects = IdeEffectsImpl(project, ownSave),
                ownSave = ownSave,
            )
        }
    }
}

// Created lazily so a project nobody looks at never runs Gradle.
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

    val gradleActivity: GradleActivity = ports.gradleActivity ?: GradleActivity()

    val gradleRunner: GradleTaskRunner = SerialGradleTaskRunner(ports.runner, gradleActivity)

    private val fileSystem: ProjectFileSystem = ports.fileSystem
    private val syncedProject: SyncedProjectSource = ports.syncedProject
    private val ownSave: OwnSaveMark = ports.ownSave
    private val effects: IdeEffects = ports.effects
    private val importInProgress: () -> Boolean = ports.importInProgress ?: ::platformImportInProgress
    private val refreshNotifications: () -> Unit = ports.refreshNotifications ?: ::updateAllNotifications
    private val buildIndex: (List<DescriptionSnapshot>) -> TemplatePlacementIndex =
        ports.buildIndex ?: { snapshots -> TemplatePlacementIndex.build(snapshots) { placementRootOf(it, fileSystem) } }

    private val importsRunning = AtomicInteger(0)

    val ledger: EntryGenerationLedger = EntryGenerationLedger()

    val entryEffects: EntryEffects = ports.entryEffects ?: EntryEffectsImpl(project)

    private val mutablePlacementIndex = MutableStateFlow<PlacementIndexState>(PlacementIndexState.NotLoaded)

    val placementIndex: StateFlow<PlacementIndexState> = mutablePlacementIndex.asStateFlow()

    val viewModel: KatachiToolWindowViewModel = KatachiToolWindowViewModel(
        scope = scope,
        syncedProject = ports.syncedProject,
        runner = gradleRunner,
        fileSystem = ports.fileSystem,
        effects = effects,
        initial = KatachiScreenState(
            form = settings?.let { FormState(onExisting = it.onExisting) } ?: FormState(),
            view = settings?.let { ViewState(collapsedModules = it.collapsedModules) } ?: ViewState(),
        ),
        loadsWithoutUser = ::loadsWithoutUser,
        candidateLister = IdeDirectoryLister(project),
    )

    private val definitionPublisher = DefinitionStatePublisher(scope, viewModel, loadsWithoutUser = ::loadsWithoutUser)

    val definitions: StateFlow<DefinitionState> = definitionPublisher.state

    val definitionErrors: StateFlow<DefinitionErrors> = definitionPublisher.errors

    val siteResolver: DeclarationSiteResolver = DeclarationSiteResolver(project, scope)

    private val autoReload = AutoReload(scope, viewModel.state, gradleActivity, ports.autoReloadDelayMillis) {
        withContext(Dispatchers.EDT) { viewModel.reloadIfIdle() }
    }

    val isAutoReloadPending: Boolean get() = autoReload.isPending

    val entryGeneration: SingleFileGeneration = SingleFileGeneration(effects, gradleRunner, fileSystem, this, ledger)

    fun generateFromEntry(request: SingleFileGenerationRequest): Deferred<SingleFileGenerationResult> =
        scope.async(Dispatchers.Default) {
            entryGeneration.run(request).also { result ->
                if (result is SingleFileGenerationResult.Refused) effects.notifyEntryGenerationRefused(result.refusal)
            }
        }

    init {
        // Without it the automatic reload waits only for this plugin's own builds (design U-12).
        if (ports.gradleActivity == null) watchIdeGradle(project, gradleActivity, this)
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
                        siteResolver.invalidateRoots()
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

    fun onFilesChanged(changes: FileChanges) {
        // A directory created or removed changes the candidates of the capture fields on screen (design 3.3). This is called
        // inside the VFS's write action: the roots are looked up on the disk, so leave it (the ViewModel is thread-safe there).
        if (changes.projectChanged) scope.launch { viewModel.onFilesChanged() }
        if (changes.definitionChanged.isEmpty() || ownSave.isSaving || viewModel.definitionModules.isEmpty()) return
        dispatchOnEdt(KatachiIntent.DefinitionChanged)
        definitionPublisher.markStale()
        siteResolver.invalidate(changes.definitionChanged)
        if (changes.testSourcesChanged.isEmpty() || !autoReloadsOnSave() || !viewModel.loadStarted.value) return
        autoReload.schedule()
    }

    fun refreshDefinition(reason: RefreshReason) {
        dispatchOnEdt(if (reason == RefreshReason.SyncCompleted) KatachiIntent.SyncCompleted else KatachiIntent.Reload)
    }

    fun watchedModules(): List<WatchedModule> = definitionModules().map { WatchedModule(it, siteResolver.testRootsOf(it)) }

    fun definitionModules(): List<KatachiModule> =
        viewModel.definitionModules.ifEmpty { modulesInSyncedData ?: readModulesInSyncedData().also { modulesInSyncedData = it } }

    @Volatile private var modulesInSyncedData: List<KatachiModule>? = null

    private fun readModulesInSyncedData(): List<KatachiModule> =
        when (val result = sdkCall("detect the katachi definition modules") { detectDefinitionModules(syncedProject.read()) }.getOrNull()) {
            is DetectionResult.Found -> result.modules
            is DetectionResult.TaskListMissing -> result.candidates
            else -> emptyList()
        }

    fun projectRoots(): List<Path> {
        val answered = definitions.value.answer?.definitions.orEmpty().mapNotNull { it.definition?.projectRoot }
        return answered.ifEmpty { definitionModules().map { it.linkedRootPath }.distinct() }
    }

    private fun autoReloadsOnSave(): Boolean =
        settings?.let { sdkCall("read the reload-when-saved setting") { it.autoReloadOnSave }.getOrNull() } == true

    private fun loadsWithoutUser(): Boolean =
        settings?.let { sdkCall("read the auto-load setting") { it.autoLoadTemplates }.getOrNull() } ?: true

    private val ensureScheduled = AtomicBoolean(false)

    @Volatile private var ensureAfterImport = false

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

    override fun registerOwnWrite(path: Path) = viewModel.registerOwnWrite(path)

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
        modulesInSyncedData = null
    }

    private fun platformImportInProgress(): Boolean = sdkCall("ask whether Gradle is importing") {
        ExternalSystemProcessingManager.getInstance().hasTaskOfTypeInProgress(ExternalSystemTaskType.RESOLVE_PROJECT, project)
    }.getOrNull() == true

    private fun updateAllNotifications() {
        if (!project.isDisposed) EditorNotifications.getInstance(project).updateAllNotifications()
    }

    // The ViewModel is driven from the EDT only; IDE listeners may call from anywhere.
    private fun dispatchOnEdt(intent: KatachiIntent) {
        scope.launch(Dispatchers.EDT) { viewModel.dispatch(intent) }
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): KatachiProjectService = project.service()
    }
}
