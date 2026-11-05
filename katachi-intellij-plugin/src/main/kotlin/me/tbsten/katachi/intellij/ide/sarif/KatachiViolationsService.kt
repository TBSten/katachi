package me.tbsten.katachi.intellij.ide.sarif

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.sarif.ViolationsStore
import me.tbsten.katachi.intellij.data.watch.FileChanges
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiNotifications
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import java.nio.file.Path

@Service(Service.Level.PROJECT)
internal class KatachiViolationsService(
    private val project: Project,
    private val store: ViolationsStore,
) : ViolationsService {
    @Suppress("unused") // Called by the platform.
    constructor(project: Project, scope: CoroutineScope) : this(project, storeOf(project, scope))

    init {
        store.refresh()
    }

    override val state: StateFlow<ViolationsState> get() = store.state

    override val runningChecks: StateFlow<Set<ModuleId>> get() = store.runningChecks

    override fun forFile(file: VirtualFile): List<ViolationModel> {
        val path = sdkCall("read the path of $file") { file.fileSystem.getNioPath(file) }.getOrNull() ?: return emptyList()
        return store.forPath(path)
    }

    override fun forModule(module: ModuleId): List<ViolationModel> = store.forModule(module)

    override fun forPath(path: Path): List<ViolationModel> = store.forPath(path)

    override fun refresh() {
        store.refresh()
    }

    override fun runCheck(module: ModuleId): Deferred<GradleRunOutcome> = store.runCheck(module)

    fun onFilesChanged(changes: FileChanges) = store.onFilesChanged(changes)

    private companion object {
        val LOG = logger<KatachiViolationsService>()

        fun storeOf(project: Project, scope: CoroutineScope): ViolationsStore {
            val projectService = KatachiProjectService.getInstance(project)
            val store = ViolationsStore(
                scope = scope,
                runner = projectService.gradleRunner,
                fileSystem = NioProjectFileSystem,
                modules = projectService::definitionModules,
                onSarifMissing = { module -> notifySarifNotConfigured(project, module) },
                beforeRead = ::loadReportDirectoriesIntoVfs,
                onError = { message, cause -> LOG.warn(message, cause) },
            )
            // The SARIF already on disk is read once the definition modules are known, and again when they change.
            scope.launch { projectService.viewModel.state.map { it.modules }.distinctUntilChanged().collect { store.refresh() } }
            return store
        }

        fun notifySarifNotConfigured(project: Project, module: KatachiModule) {
            if (project.isDisposed) return
            sdkCall("tell that ${module.gradlePath} wrote no SARIF") {
                NotificationGroupManager.getInstance().getNotificationGroup(KatachiNotifications.GROUP_ID)
                    .createNotification(KatachiBundle.message("violations.sarifNotConfigured", module.gradlePath), NotificationType.WARNING)
                    .notify(project)
            }
        }
    }
}
