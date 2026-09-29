package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Files
import java.nio.file.Path

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
    private val sourceRoots = mutableListOf<VirtualFile>()

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
            releaseSourceRoots()
            dropUnsavedDocuments()
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

    /**
     * Makes [dir] (created when missing) a source root of the test module, as the notification's and the
     * New menu's package lookup needs. Released by [releaseSourceRoots], which `tearDown` calls, so it does
     * not stay for the next test.
     */
    protected fun addSourceRoot(dir: Path, packagePrefix: String = ""): VirtualFile {
        Files.createDirectories(dir)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir) ?: throw AssertionError("Not in the VFS: $dir")
        PsiTestUtil.addSourceRoot(module, file, JavaSourceRootType.SOURCE, JpsJavaExtensionService.getInstance().createSourceRootProperties(packagePrefix))
        sourceRoots += file
        return file
    }

    /**
     * Removes what [addSourceRoot] added, content entries included: `removeSourceRoot` alone leaves the
     * content root behind, which leaks into the next test (S2 (f)).
     */
    protected fun releaseSourceRoots() {
        val added = sourceRoots.toList()
        sourceRoots.clear()
        added.filter { it.isValid }.forEach { PsiTestUtil.removeContentEntry(module, it) }
    }

    /**
     * A file on disk with [onDisk] whose open Document holds [inMemory] instead, unsaved: the state of a
     * user who has typed into the editor. `tearDown` drops it so it does not turn the next test's
     * conflict into a silent one.
     */
    protected fun unsavedDocument(path: Path, onDisk: String, inMemory: String): Document {
        val file = WriteAction.compute<VirtualFile, Throwable> {
            val dir = VfsUtil.createDirectoryIfMissing(path.parent.toString()) ?: throw AssertionError("No directory: ${path.parent}")
            val child = dir.findChild(path.fileName.toString()) ?: dir.createChildData(this, path.fileName.toString())
            VfsUtil.saveText(child, onDisk)
            child
        }
        val document = FileDocumentManager.getInstance().getDocument(file) ?: throw AssertionError("No document: $path")
        WriteCommandAction.runWriteCommandAction(project) { document.setText(inMemory) }
        return document
    }

    private fun dropUnsavedDocuments() {
        val documents = FileDocumentManager.getInstance()
        if (documents.unsavedDocuments.isNotEmpty()) WriteAction.run<Throwable> { (documents as FileDocumentManagerImpl).dropAllUnsavedDocuments() }
    }
}
