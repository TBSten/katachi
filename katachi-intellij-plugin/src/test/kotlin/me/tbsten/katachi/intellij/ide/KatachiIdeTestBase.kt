package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel
import me.tbsten.katachi.intellij.testing.ContractFixtures
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

/**
 * Platform tests of the IDE wiring with the real [IdeEffectsImpl] (VFS, editors, Local History,
 * dialogs) but fake synced data and a fake Gradle that writes real files under [root].
 *
 * Each test gets a fresh [KatachiProjectService] in place of the light project's shared one.
 */
internal abstract class KatachiIdeTestBase : AnalysisTestBase() {
    protected lateinit var root: Path
    lateinit var gradle: DiskGradleRunner
    internal var synced: SyncedProject = SyncedProject.NotSynced
    protected lateinit var scope: CoroutineScope

    override fun setUp() {
        super.setUp()
        // The real path: on macOS /var is a link to /private/var, and the VFS resolves it.
        root = Files.createTempDirectory("katachi-ide-test").toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
        gradle = DiskGradleRunner(root)
        synced = syncedWith(":arch-a")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun tearDown() {
        try {
            scope.cancel()
            FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
            NioFiles.deleteRecursively(root)
        } finally {
            super.tearDown()
        }
    }

    /** Installs a new [KatachiProjectService] over the fakes and returns its ViewModel. */
    internal fun installService(): KatachiToolWindowViewModel {
        val ports = KatachiPorts({ synced }, gradle, NioProjectFileSystem, IdeEffectsImpl(project))
        val service = KatachiProjectService(project, scope, ports)
        project.replaceService(KatachiProjectService::class.java, service, testRootDisposable)
        return service.viewModel
    }

    /** Synced data where [gradlePaths] have `katachiInternalTemplatesJson`, under [root]. */
    internal fun syncedWith(vararg gradlePaths: String): SyncedProject = SyncedProject.Synced(
        listOf(
            SyncedRoot(
                root,
                "project",
                gradlePaths.map { SyncedModule(it, moduleDirOf(it), setOf(KatachiModule.TEMPLATES_JSON_TASK), "0.3.0") },
            ),
        ),
    )

    protected fun moduleDirOf(gradlePath: String): Path = root.resolve(gradlePath.trim(':'))

    /** Pumps the EDT until [condition] holds on the ViewModel's state. */
    internal fun KatachiToolWindowViewModel.waitFor(what: String, condition: (KatachiScreenState) -> Boolean): KatachiScreenState {
        PlatformTestUtil.waitWithEventsDispatching({ "$what: ${state.value}" }, { condition(state.value) }, 10)
        return state.value
    }

    protected fun openFiles(): List<Path> = FileEditorManager.getInstance(project).openFiles.mapNotNull { it.fileSystem.getNioPath(it) }
}

/**
 * A Gradle that "loads" by writing the contract JSON into the module and "generates" by writing the
 * files the contract output names, then printing that output with [root] in place of the token.
 */
internal class DiskGradleRunner(private val root: Path) : GradleTaskRunner {
    val requests: MutableList<GradleRunRequest> = Collections.synchronizedList(mutableListOf())

    /** The contract output (`output/<name>.log`) each `katachiTemplate` run answers with, in order. */
    val generations: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** When set, a generation suspends here after printing, so a test can close the project mid-run. */
    @Volatile var gate: CompletableDeferred<Unit>? = null

    /** Completes once a generation is waiting on [gate]. */
    val reachedGate: CompletableDeferred<Unit> = CompletableDeferred()

    @Volatile var cancelled: Boolean = false
        private set

    override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        requests += request
        val task = request.tasks.first().taskPath
        return when {
            task.endsWith(":${KatachiModule.TEMPLATES_JSON_TASK}") -> {
                for (invocation in request.tasks) {
                    val module = invocation.taskPath.substringBeforeLast(':').trim(':')
                    val json = root.resolve(module).resolve(KatachiModule.TEMPLATE_DESCRIPTION_JSON)
                    Files.createDirectories(json.parent)
                    Files.writeString(json, ContractFixtures.json(module))
                }
                GradleRunOutcome.Succeeded
            }
            else -> {
                val name = if (generations.isEmpty()) "new" else generations.removeAt(0)
                val lines = ContractFixtures.outputLines(name, root)
                lines.forEach(listener::onLine)
                gate?.let { gate ->
                    reachedGate.complete(Unit)
                    try {
                        gate.await()
                    } catch (e: CancellationException) {
                        cancelled = true
                        throw e
                    }
                }
                writeReportedFiles(lines)
                if (ContractFixtures.exitCode(name) == 0) GradleRunOutcome.Succeeded else GradleRunOutcome.Failed
            }
        }
    }

    private fun writeReportedFiles(lines: List<String>) {
        for (line in lines) {
            val uri = line.substringAfter("[template] Wrote ", missingDelimiterValue = "").takeIf { it.startsWith("file:") } ?: continue
            val path = Path.of(java.net.URI(uri))
            Files.createDirectories(path.parent)
            Files.writeString(path, "// written by the fake Gradle\n")
        }
    }
}
