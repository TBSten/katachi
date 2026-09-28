package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.data.placement.placementRootOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Platform tests of [KatachiProjectService] as the New menu and the editor notification use it
 * (issues 11, 18): the service is created without the tool window, the import state is played with
 * [importing], and the notification updates and the index builds are recorded.
 */
internal abstract class EntryServiceTestBase : KatachiIdeTestBase() {
    /** What the fake import state answers; the listener's own count adds to it. */
    @Volatile var importing: Boolean = false

    /** The index state at each notification update, in order. */
    val notificationUpdates: MutableList<PlacementIndexState> = CopyOnWriteArrayList()

    /** For each index build, whether it ran on the EDT. */
    val indexBuildsOnEdt: MutableList<Boolean> = CopyOnWriteArrayList()

    lateinit var service: KatachiProjectService

    private val settings get() = KatachiSettings.getInstance(project)
    private var savedAutoLoad = true
    private var savedAutoReload = false

    override fun setUp() {
        super.setUp()
        savedAutoLoad = settings.autoLoadTemplates
        savedAutoReload = settings.autoReloadOnSave
    }

    override fun tearDown() {
        try {
            // The light project's settings outlive the test.
            settings.autoLoadTemplates = savedAutoLoad
            settings.autoReloadOnSave = savedAutoReload
        } finally {
            super.tearDown()
        }
    }

    /** "Load without a user action" (decision 16). */
    protected fun setAutoLoad(on: Boolean) {
        settings.autoLoadTemplates = on
    }

    protected fun setAutoReloadOnSave(on: Boolean) {
        settings.autoReloadOnSave = on
    }

    /** A new service over the fakes, as an entry would create it: no tool window. */
    internal fun installEntryService(runner: GradleTaskRunner = gradle): KatachiProjectService {
        val created = AtomicReference<KatachiProjectService>()
        val ports = KatachiPorts(
            syncedProject = { synced },
            runner = runner,
            fileSystem = NioProjectFileSystem,
            effects = IdeEffectsImpl(project),
            importInProgress = { importing },
            refreshNotifications = { created.get()?.let { notificationUpdates += it.placementIndex.value } },
            buildIndex = { snapshots ->
                indexBuildsOnEdt += ApplicationManager.getApplication().isDispatchThread
                TemplatePlacementIndex.build(snapshots) { placementRootOf(it, NioProjectFileSystem) }
            },
        )
        val service = KatachiProjectService(project, scope, ports)
        created.set(service)
        project.replaceService(KatachiProjectService::class.java, service, testRootDisposable)
        this.service = service
        return service
    }

    /** Pumps the EDT until the index satisfies [condition]. */
    internal fun waitForIndex(what: String, condition: (PlacementIndexState) -> Boolean): PlacementIndexState {
        PlatformTestUtil.waitWithEventsDispatching({ "$what: ${service.placementIndex.value}" }, { condition(service.placementIndex.value) }, 10)
        return service.placementIndex.value
    }

    /**
     * Runs what the EDT has queued (the hops of `ensureLoaded`, which start any load at once), then
     * waits for the detection or load that started, if any: after this, nothing more is on its way.
     */
    protected fun settle() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        PlatformTestUtil.waitWithEventsDispatching({ "a load still runs: ${service.viewModel.state.value}" }, { !service.viewModel.isLoadRunning }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    /** Calls `ensureLoaded()` [times] times from each of [threads] background threads and waits for them. */
    protected fun ensureLoadedFromBackground(threads: Int = 4, times: Int = 50) {
        val workers = List(threads) { Thread { repeat(times) { service.ensureLoaded() } } }
        workers.forEach(Thread::start)
        workers.forEach { it.join(10_000) }
    }

    protected fun publishImportStarted() {
        project.messageBus.syncPublisher(ProjectDataImportListener.TOPIC).onImportStarted(root.toString())
    }

    /** What the platform sends when a Gradle sync ends. */
    protected fun publishImportFinished() {
        project.messageBus.syncPublisher(ProjectDataImportListener.TOPIC).onImportFinished(root.toString())
    }
}
