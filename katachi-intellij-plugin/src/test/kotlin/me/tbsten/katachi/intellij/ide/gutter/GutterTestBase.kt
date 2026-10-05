package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.ThrowableRunnable
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.sarif.FakeViolationsService
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.FakeCaptureSeedPort
import me.tbsten.katachi.intellij.testing.FakeEntryEffects
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.Collections

/** The directory `configureByText` puts light files in. */
internal val LIGHT_ROOT: Path = Paths.get("/src")

/** One ◆ as the daemon drew it: its 1-based line, the text of the leaf it is keyed on, and its tooltip. */
internal data class DrawnMark(val line: Int, val leaf: String, val tooltip: String?, val isLeaf: Boolean, val hasMenu: Boolean)

/**
 * Platform tests of [KatachiDeclarationLineMarkerProvider] over light files and the foundation's fakes: the provider is
 * registered for Kotlin with ports that answer from [definitions] and [violations], the real PSI and daemon draw it.
 *
 * The file's write time is not read from the VFS (a light file is always newer than the fake definition): the test world
 * keeps it. [saveAll] writes the file when its Document had edits ([writtenAt], after the definition's `generatedAt`), and
 * [refreshedSnapshot] is a definition written after that, as the foundation's refresh makes it. [modifiedAt] overrides
 * both for a test that sets a time by hand; [realFileState] reads the VFS's `timeStamp` instead, as the platform does.
 * Unsaved edits are the real `FileDocumentManager`'s.
 */
internal abstract class GutterTestBase : AnalysisTestBase() {
    lateinit var definitions: FakeDefinitionModelService
    lateinit var violations: FakeViolationsService
    lateinit var provider: KatachiDeclarationLineMarkerProvider

    /** Whether the synced data has a definition module. */
    @Volatile var hasDefinitionModule: Boolean = true

    /** When the open file was last written, set by hand; `null`: [writtenAt] (the world's own). */
    @Volatile var modifiedAt: Instant? = null

    /** When the world last wrote the open file: [saveAll] with edits in its Document. `null`: never. */
    @Volatile var writtenAt: Instant? = null

    /** Whether the ports read the file's time from the VFS (`GutterPorts.fileStateOf`) instead of the world's. */
    @Volatile var realFileState: Boolean = false

    private var ticks = 0L

    /** A time after every earlier [tick] and after [DEFINITION_INSTANT]. */
    fun tick(): Instant = DEFINITION_INSTANT.plusSeconds(++ticks)

    override fun setUp() {
        super.setUp()
        definitions = FakeDefinitionModelService()
        violations = FakeViolationsService()
        provider = KatachiDeclarationLineMarkerProvider { ports() }
        LineMarkerProviders.getInstance().addExplicitExtension(KotlinLanguage.INSTANCE, provider, testRootDisposable)
    }

    // configureByText starts bundled plugins (the Vue LSP) that log errors in this IDE build.
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        ignoreUnrelatedLoggedErrors { super.runTestRunnable(testRunnable) }
    }

    /** What the menu asked of the IDE: the generate dialog it opened, the balloons it showed. */
    val effects = FakeEntryEffects()
    val seedPort = FakeCaptureSeedPort()
    val balloons: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** The platform actions the menu finds by id (`katachi.RunCheck`, `katachi.RefreshDefinition`); none until a test adds one. */
    val actions: MutableMap<String, AnAction> = mutableMapOf()

    fun ports(): GutterPorts = GutterPorts(
        hasDefinitionModule = { hasDefinitionModule },
        definitions = { definitions },
        violations = { violations },
        fileState = { file ->
            if (realFileState) GutterPorts.fileStateOf(file)
            else GutterPorts.FileState(modifiedAt ?: writtenAt, FileDocumentManager.getInstance().isFileModified(file))
        },
        effects = { effects },
        seeds = { seedPort },
        action = { id -> actions[id] },
        notify = { balloons += it },
    )

    /** A ready definition whose answer for the light file [fileName] is [roles] and [explanations]. */
    fun ready(fileName: String, roles: FileRoles?, explanations: List<RoleFileDeclarations> = emptyList(), freshness: Freshness = Freshness.Fresh) {
        val path = lightPath(fileName)
        definitions.answer(path, roles)
        definitions.explain(path, explanations)
        definitions.emit(DefinitionState.Ready(listOf(snapshot), freshness))
    }

    /** The definition the fake serves, rooted where the light files are, so that the real refresher finds the open file under it. */
    var snapshot = definitionSnapshot(definitionModel(projectRoot = LIGHT_ROOT))

    /** What a refresh of the definition leaves in [snapshot]: the same definition, written now. */
    fun refreshedSnapshot(): DefinitionSnapshot {
        val now = tick()
        snapshot = definitionSnapshot(snapshot.definition?.copy(generatedAt = now), snapshot.module)
        return snapshot
    }

    private var localRoot: Path? = null

    /**
     * Has [open] put the files on the local disk instead of the light file system. The platform gives a light file no
     * `getNioPath`, so the real [HighlightingRefresher] cannot find it under a root; a local file is found as in the IDE. The
     * definition is rooted at the directory.
     */
    fun useLocalFiles() {
        val directory = Files.createTempDirectory("katachi-gutter-local").toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.toString())
        localRoot = directory
        // The daemon highlights only files under a source folder.
        val directoryFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory) ?: error("$directory was not found after it was made")
        PsiTestUtil.addSourceRoot(myFixture.module, directoryFile)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(myFixture.module, directoryFile) }
        snapshot = definitionSnapshot(snapshot.definition?.copy(projectRoot = directory), snapshot.module)
    }

    override fun tearDown() {
        try {
            localRoot?.let { FileUtil.delete(it.toFile()) }
        } finally {
            super.tearDown()
        }
    }

    /** Where [open] puts [fileName]: a light file, or a file under the [useLocalFiles] directory. */
    fun lightPath(fileName: String): Path = (localRoot ?: LIGHT_ROOT).resolve(fileName)

    /** Opens [text] as [fileName] in the editor, replacing what an earlier call (and the edits to it) left. */
    fun open(fileName: String, text: String): PsiFile {
        val directory = localRoot ?: return myFixture.configureByText(fileName, text)
        val path = directory.resolve(fileName)
        val known = LocalFileSystem.getInstance().findFileByNioFile(path)
        if (known != null) {
            FileEditorManager.getInstance(project).closeFile(known)
            FileDocumentManager.getInstance().getCachedDocument(known)?.let { FileDocumentManager.getInstance().reloadFromDisk(it) }
        }
        Files.writeString(path, text)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: error("$path was not found after it was written")
        file.refresh(false, false)
        myFixture.configureFromExistingVirtualFile(file)
        return myFixture.file
    }

    /** Opens [text] as [fileName] and returns the katachi ◆ the daemon drew. */
    fun drawn(fileName: String, text: String): List<DrawnMark> {
        open(fileName, text)
        return redrawn()
    }

    /**
     * The katachi ◆ of the open file after the daemon ran again. The test restarts the daemon itself, which stands for
     * [HighlightingRefresher] (the daemon does not recompute an unchanged file by itself); [redrawnByRefresher] leaves
     * that to the real one.
     */
    fun redrawn(): List<DrawnMark> {
        DaemonCodeAnalyzer.getInstance(project).restart(myFixture.file, "katachi test: the fake's answer changed")
        myFixture.doHighlighting()
        return markersDrawn()
    }

    private var statesSeen = 0

    /**
     * The katachi ◆ of the open file with no restart from the test: the real [HighlightingRefresher] restarts the daemon
     * when a state changed (after its settling time), and an edit makes the daemon run by itself. A ◆ that is still old
     * after a change means the refresher did not reach the open file.
     */
    fun redrawnByRefresher(): List<DrawnMark> {
        if (definitions.history.size != statesSeen) {
            statesSeen = definitions.history.size
            val deadline = System.currentTimeMillis() + HighlightingRefresher.SETTLE_MILLIS + 500
            PlatformTestUtil.waitWithEventsDispatching("the refresher settles", { System.currentTimeMillis() > deadline }, 5)
        }
        myFixture.doHighlighting()
        return markersDrawn()
    }

    private fun markersDrawn(): List<DrawnMark> = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
        .filter { it.lineMarkerTooltip?.startsWith("katachi:") == true }
        .map(::drawnOf)
        .sortedBy { it.line }

    /** The menus of the katachi ◆ the daemon drew in the open file, top to bottom. */
    fun menus(): List<GutterMenu> = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
        .filter { it.lineMarkerTooltip?.startsWith("katachi:") == true }
        .sortedBy { it.startOffset }
        .mapNotNull { it.navigationHandler as? GutterMenu }

    /** What the provider gives for every element of [file] at once, as the daemon would ask it; works in dumb mode and for `.kts`. */
    fun collectedDirectly(file: PsiFile): List<DrawnMark> = collectedInfos(file).map(::drawnOf).sortedBy { it.line }

    /** [collectedDirectly] before it is read as [DrawnMark]. */
    fun collectedInfos(file: PsiFile): List<LineMarkerInfo<*>> {
        val elements = SyntaxTraverser.psiTraverser(file).toList()
        val result = mutableListOf<LineMarkerInfo<*>>()
        runReadActionBlocking { provider.collectSlowLineMarkers(elements.toMutableList(), result) }
        return result.sortedBy { it.startOffset }
    }

    /** Saves every Document. Not a lambda in a test: JUnit 3 reads a no-argument `test…$lambda` method as a test that is not public. */
    fun saveAll() = WriteCommandAction.runWriteCommandAction(project) {
        // A save with nothing to write leaves the file's time alone, as the VFS does.
        val edited = myFixture.file?.virtualFile?.let { FileDocumentManager.getInstance().isFileModified(it) } == true
        FileDocumentManager.getInstance().saveAllDocuments()
        if (edited) writtenAt = tick()
    }

    /** Types [text] at the start of the open file without saving it. */
    fun typeAtStart(text: String) = WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.insertString(0, text) }

    private fun drawnOf(info: LineMarkerInfo<*>): DrawnMark {
        val element: PsiElement? = info.element
        val document = myFixture.editor.document
        return DrawnMark(
            line = document.getLineNumber(info.startOffset) + 1,
            leaf = element?.text.orEmpty(),
            tooltip = info.lineMarkerTooltip,
            isLeaf = element?.firstChild == null,
            hasMenu = info.navigationHandler != null,
        )
    }
}
