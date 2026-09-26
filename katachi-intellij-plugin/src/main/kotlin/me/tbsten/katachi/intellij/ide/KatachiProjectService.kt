package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.detect.SyncedProjectSource
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.presentation.FormState
import me.tbsten.katachi.intellij.presentation.IdeEffects
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel
import me.tbsten.katachi.intellij.presentation.ViewState

/** The ports the ViewModel runs on. Tests swap the synced data and Gradle for fakes. */
internal data class KatachiPorts(
    val syncedProject: SyncedProjectSource,
    val runner: GradleTaskRunner,
    val fileSystem: ProjectFileSystem,
    val effects: IdeEffects,
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
 */
@Service(Service.Level.PROJECT)
internal class KatachiProjectService(
    private val project: Project,
    val scope: CoroutineScope,
    ports: KatachiPorts,
) : Disposable {
    @Suppress("unused") // Called by the platform.
    constructor(project: Project, scope: CoroutineScope) : this(project, scope, KatachiPorts.of(project))

    private val settings = KatachiSettings.getInstance(project)

    val viewModel: KatachiToolWindowViewModel = KatachiToolWindowViewModel(
        scope = scope,
        syncedProject = ports.syncedProject,
        runner = ports.runner,
        fileSystem = ports.fileSystem,
        effects = ports.effects,
        initial = KatachiScreenState(
            form = FormState(onExisting = settings.onExisting),
            view = ViewState(collapsedModules = settings.collapsedModules),
        ),
    )

    init {
        project.messageBus.connect(this).subscribe(
            ProjectDataImportListener.TOPIC,
            object : ProjectDataImportListener {
                override fun onImportFinished(projectPath: String?) {
                    dispatchOnEdt(KatachiIntent.SyncCompleted)
                }
            },
        )
        // What the user picks in the combo and folds stays with the project (spec 04 "入力値の保存").
        // On the EDT, like the settings UI, so that two writes of the set never overlap.
        scope.launch(Dispatchers.EDT) {
            viewModel.state.map { it.form.onExisting to it.view.collapsedModules }.distinctUntilChanged().collect { (onExisting, collapsed) ->
                settings.onExisting = onExisting
                settings.collapsedModules = collapsed
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
        if (!settings.autoReloadOnSave || viewModel.state.value.loading != null) return
        autoReload?.cancel()
        autoReload = scope.launch {
            delay(AUTO_RELOAD_DELAY_MILLIS)
            withContext(Dispatchers.EDT) { viewModel.dispatch(KatachiIntent.Reload) }
        }
    }

    /** The ViewModel is driven from the EDT only; IDE listeners call from anywhere. */
    private fun dispatchOnEdt(intent: KatachiIntent) {
        scope.launch(Dispatchers.EDT) { viewModel.dispatch(intent) }
    }

    override fun dispose() = Unit

    companion object {
        private const val AUTO_RELOAD_DELAY_MILLIS = 2_000L

        fun getInstance(project: Project): KatachiProjectService = project.service()
    }
}
