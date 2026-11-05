package me.tbsten.katachi.intellij.ide

import com.intellij.history.LocalHistory
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.generate.EntryGenerationFailure
import me.tbsten.katachi.intellij.data.generate.EntryGenerationRefusal
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.ide.definition.DeclarationSiteResolver
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterNavigation
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.DocsPage
import me.tbsten.katachi.intellij.presentation.IdeEffects
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.nio.file.Path

// SDK failures must not reach the ViewModel as exceptions: that would turn a finished generation into a failed one.
internal class IdeEffectsImpl(
    private val project: Project,
    private val ownSave: OwnSaveMark = OwnSaveMark(),
    private val siteResolver: () -> DeclarationSiteResolver? = { KatachiProjectService.getInstance(project).siteResolver },
    private val entrySdkProbe: (EntrySdkCall) -> Unit = {},
) : IdeEffects {
    override suspend fun saveAllDocuments() {
        onEdt {
            ownSave.around { sdkCall("save all documents") { writeIntentReadAction { FileDocumentManager.getInstance().saveAllDocuments() } } }
        }
    }

    override suspend fun putLocalHistoryLabel(titles: List<String>): String? =
        putLocalHistoryLabelNamed(KatachiBundle.message("localHistory.label", titles.joinToString(", ")))

    override suspend fun putLocalHistoryLabelNamed(name: String): String? {
        val put = onEdt { sdkCall("put the Local History label \"$name\"") { LocalHistory.getInstance().putSystemLabel(project, name) } }
        return name.takeIf { put?.isSuccess == true }
    }

    override suspend fun refreshFiles(paths: List<Path>) {
        if (paths.isEmpty() || project.isDisposed) return
        withContext(Dispatchers.IO) {
            sdkCall("refresh ${paths.size} generated files in the VFS") {
                val fileSystem = LocalFileSystem.getInstance()
                val loaded = paths.mapNotNull { fileSystem.findFileByNioFile(it) }
                val newParents = paths.filter { fileSystem.findFileByNioFile(it) == null }.mapNotNull(::knownAncestorOf).distinct()
                if (loaded.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, false, false, *loaded.toTypedArray())
                if (newParents.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, true, true, *newParents.toTypedArray())
                paths.forEach { fileSystem.refreshAndFindFileByNioFile(it) }
            }
        }
    }

    override suspend fun openFiles(paths: List<Path>): List<Path> {
        if (paths.isEmpty()) return emptyList()
        val files = withContext(Dispatchers.IO) {
            paths.mapNotNull { path ->
                sdkCall("find $path in the VFS") { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) }.getOrNull()?.let { path to it }
            }
        }
        return onEdt {
            // Opening a file selects its tab, so open them all in order and then select the first again.
            val opened = files.filter { (path, file) -> sdkCall("open $path in an editor") { OpenFileDescriptor(project, file).navigateInEditor(project, false) }.getOrNull() == true }
            opened.firstOrNull()?.let { (path, file) -> sdkCall("select $path") { OpenFileDescriptor(project, file).navigateInEditor(project, true) } }
            opened.map { (path, _) -> path }
        }.orEmpty()
    }

    override suspend fun askConflict(question: ConflictQuestion): ConflictChoice =
        onEdt { ConflictDialogs.ask(project, question) } ?: ConflictChoice.Stop

    override fun openAfterGeneration(): OpenAfterGeneration =
        sdkCall("read the katachi settings") { KatachiSettings.getInstance(project).openAfterGeneration }.getOrDefault(OpenAfterGeneration.First)

    override fun notifyGenerationFinished(report: GenerationReport) = later("notify that the generation finished") {
        KatachiNotifications.generationFinished(project, report)
    }

    override fun notifyLoadFailed() = later("notify that loading failed") { KatachiNotifications.loadFailed(project) }

    override fun showLog() = later("show the Gradle log") {
        val manager = ToolWindowManager.getInstance(project)
        (manager.getToolWindow(ToolWindowId.RUN) ?: manager.getToolWindow(BUILD_TOOL_WINDOW_ID))?.activate(null)
    }

    override fun syncGradle() = later("sync the Gradle project") {
        ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
    }

    override fun openDocs(page: DocsPage) {
        sdkCall("open the documentation") { BrowserUtil.browse(docsUrlOf(page)) }
    }

    override suspend fun openDeclaration(module: KatachiModule, roleName: String, site: DeclarationSiteRef?) {
        val resolved = site?.let { ref ->
            withContext(Dispatchers.IO) { sdkCall("find ${ref.fileName} of the definition") { siteResolver()?.resolve(module, ref) }.getOrNull() }
        }
        if (resolved !is ResolvedSite.Found) {
            warn(KatachiBundle.message("gutter.notFound", roleName))
            return
        }
        onEdt { GutterNavigation(project) { warn(it) }.openLine(resolved.file, resolved.line) }
    }

    override fun copyToClipboard(text: String) {
        sdkCall("copy to the clipboard") { CopyPasteManager.getInstance().setContents(StringSelection(text)) }
    }

    override suspend fun createDirectories(directory: Path): Boolean = onEdt {
        sdkCall("create the directory $directory") {
            entrySdkProbe(EntrySdkCall.WriteAction)
            writeIntentReadAction {
                WriteAction.compute<VirtualFile?, IOException> { VfsUtil.createDirectoryIfMissing(directory.toString()) }
            } ?: throw IOException("The VFS did not create $directory")
        }.isSuccess
    } == true

    override suspend fun writeProvisionalFile(path: Path, text: String): Boolean {
        val file = onEdt {
            sdkCall("write the provisional file $path") {
                entrySdkProbe(EntrySdkCall.WriteAction)
                writeIntentReadAction { WriteAction.compute<VirtualFile, IOException> { writeFile(path, text) } }
            }.getOrNull()
        } ?: return false
        val opened = onEdt {
            sdkCall("open $path in an editor") {
                entrySdkProbe(EntrySdkCall.Editor)
                OpenFileDescriptor(project, file).navigateInEditor(project, true)
            }.getOrNull() == true
        }
        if (opened == false) warn(KatachiBundle.message("generate.failed.open", path.fileName.toString()))
        return true
    }

    // Call inside a write action.
    private fun writeFile(path: Path, text: String): VirtualFile {
        val directory = VfsUtil.createDirectoryIfMissing(path.parent.toString()) ?: throw IOException("The VFS did not create ${path.parent}")
        val name = path.fileName.toString()
        val file = directory.findChild(name) ?: directory.createChildData(this, name)
        VfsUtil.saveText(file, text)
        return file
    }

    override suspend fun currentText(path: Path): CharSequence? = withContext(Dispatchers.IO) {
        val shown = sdkCall("read the Document of $path") {
            readAction { cachedDocumentOf(path)?.immutableCharSequence }
        }.getOrNull()
        shown ?: NioProjectFileSystem.readText(path)
    }

    override suspend fun hasUnsavedChanges(path: Path): Boolean = withContext(Dispatchers.IO) {
        sdkCall("ask whether $path has unsaved changes") {
            readAction { cachedDocumentOf(path)?.let { FileDocumentManager.getInstance().isDocumentUnsaved(it) } == true }
        }.getOrDefault(true)
    }

    override suspend fun saveDocument(path: Path): Boolean = onEdt {
        sdkCall("save $path") {
            writeIntentReadAction { cachedDocumentOf(path)?.let { FileDocumentManager.getInstance().saveDocument(it) } }
        }.isSuccess
    } == true

    override suspend fun packageNameOf(directory: Path): String? = withContext(Dispatchers.IO) {
        sdkCall("find the package of $directory") { readAction { packageOfDirectory(project, directory) } }.getOrNull()
    }

    override suspend fun reloadFromDisk(paths: List<Path>) {
        if (paths.isEmpty()) return
        onEdt {
            for (path in paths) {
                sdkCall("reload $path from disk") {
                    val documents = FileDocumentManager.getInstance()
                    val document = cachedDocumentOf(path) ?: return@sdkCall
                    val disk = NioProjectFileSystem.readText(path) ?: return@sdkCall
                    if (!documents.isDocumentUnsaved(document) && !document.immutableCharSequence.contentEquals(disk)) {
                        writeIntentReadAction { documents.reloadFromDisk(document, project) }
                    }
                }
            }
        }
    }

    override fun notifyEntryGenerationFailed(failure: EntryGenerationFailure) = warn(entryFailureText(failure))

    override fun notifyEntryGenerationRefused(refusal: EntryGenerationRefusal) = warn(entryRefusalText(refusal))

    override fun provisionalNotice(templateTitle: String): String =
        KatachiBundle.message("generate.provisional.header", templateTitle) + "\n" + KatachiBundle.message("generate.provisional.command")

    private fun warn(text: String) = later("show the balloon \"$text\"") {
        NotificationGroupManager.getInstance().getNotificationGroup(KatachiNotifications.GROUP_ID)
            .createNotification(text, NotificationType.WARNING)
            .notify(project)
    }

    private suspend fun <T> onEdt(block: suspend () -> T): T? = withContext(Dispatchers.EDT) {
        if (project.isDisposed) null else block()
    }

    private fun later(action: String, block: () -> Unit) {
        sdkCall("schedule: $action") {
            ApplicationManager.getApplication().invokeLater({ sdkCall(action, block) }, project.disposed)
        }
    }
}

internal enum class EntrySdkCall {
    WriteAction,

    Editor,
}

private fun cachedDocumentOf(path: Path): Document? =
    LocalFileSystem.getInstance().findFileByNioFile(path)?.let { FileDocumentManager.getInstance().getCachedDocument(it) }

private fun knownAncestorOf(path: Path): VirtualFile? {
    val fileSystem = LocalFileSystem.getInstance()
    var current: Path? = path.parent
    while (current != null) {
        fileSystem.findFileByNioFile(current)?.let { return it }
        current = current.parent
    }
    return null
}

internal fun docsUrlOf(page: DocsPage, language: String = KatachiBundle.message("toolWindow.language")): String {
    val prefix = if (language == "ja") "ja/" else ""
    return DOCS_ROOT + prefix + when (page) {
        DocsPage.WritingTemplates -> "guides/generate-code-from-template/"
        DocsPage.Install, DocsPage.Update -> "get-started/first-architecture/"
    }
}

private const val DOCS_ROOT = "https://tbsten.github.io/katachi/"

// ToolWindowId has no constant for the Build tool window.
private const val BUILD_TOOL_WINDOW_ID = "Build"
