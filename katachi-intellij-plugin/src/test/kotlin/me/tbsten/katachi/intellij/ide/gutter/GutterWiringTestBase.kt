package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.util.ThrowableRunnable
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.ide.definition.DefinitionServiceTestBase
import me.tbsten.katachi.intellij.ide.gutter.annotator.DrawnAnnotation
import me.tbsten.katachi.intellij.ide.gutter.annotator.drawnAnnotations
import me.tbsten.katachi.intellij.ide.definition.KatachiDefinitionService
import me.tbsten.katachi.intellij.ide.DefinitionModulePresence
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.sarif.KatachiViolationsService
import me.tbsten.katachi.intellij.model.DetectionResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

/**
 * The world of the gutter's wiring tests (plan P3 of the gutter): everything the platform would create is the real thing, and
 * only the edges are faked. The provider is the one `plugin.xml` registers with [GutterPorts.of]; behind it are the real
 * [KatachiDefinitionService] and [KatachiViolationsService] over the project service, and a fake Gradle that writes files.
 * The synced data is the one fake the gutter reads of the IDE (the test says what [synced] holds).
 *
 * The source file is `src/service/HealthService.kt`, a `Service` role's with a `class HealthService` the definition explains
 * (line 3). Its last write is [FILE_WRITTEN_AT]; the first definition is [firstDefinitionAt], after it, as a Gradle run that
 * came after the user's last write makes it.
 */
internal abstract class GutterWiringTestBase : DefinitionServiceTestBase() {
    protected lateinit var violations: KatachiViolationsService
    protected lateinit var sourcePath: Path

    /** When the user last wrote the source file: before everything the Gradle fake answers with. */
    protected val fileWrittenAt: Instant = Instant.now().minusSeconds(20)

    /** When the first load's definition says it was generated: after [fileWrittenAt], so the first ◆ is not stale. */
    protected val firstDefinitionAt: Instant = fileWrittenAt.plusSeconds(10)

    override fun setUp() {
        super.setUp()
        KatachiSettings.getInstance(project).autoReloadOnSave = false
        sourcePath = root.resolve(SOURCE)
        Files.createDirectories(sourcePath.parent)
        Files.writeString(sourcePath, "package service\n\nclass HealthService\n")
        Files.setLastModifiedTime(sourcePath, FileTime.from(fileWrittenAt))
        addSourceRoot(root.resolve("src"))
        gradle.definitionOf["arch"] = definitionGeneratedAt(firstDefinitionAt)
    }

    // configureFromExistingVirtualFile starts bundled plugins (the Vue LSP) that log errors in this IDE build.
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        ignoreUnrelatedLoggedErrors { super.runTestRunnable(testRunnable) }
    }

    /** The explained definition as a Gradle run at [at] writes it (the fixture's own `generatedAt` is a fixed day). */
    protected fun definitionGeneratedAt(at: Instant): String =
        definitionJson("synthetic-definition-explained").replace("\"generatedAt\": \"2026-10-04T00:30:12Z\"", "\"generatedAt\": \"$at\"")

    /**
     * What the platform has when the gutter asks: the project service over the fakes, and the three services the gutter's
     * ports create or read, replaced by the real ones built over it (the light project is shared, so the ones an earlier test made would hold its project service).
     * The "has a definition module" answer is read from [synced] by the real detector, as the cache reads the IDE's.
     */
    protected fun installReal() {
        install()
        project.replaceService(KatachiDefinitionService::class.java, definition, testRootDisposable)
        violations = KatachiViolationsService(project, scope)
        project.replaceService(KatachiViolationsService::class.java, violations, testRootDisposable)
        val cache = DefinitionModulePresence(project) { detectDefinitionModules(synced).let { it is DetectionResult.Found || it is DetectionResult.TaskListMissing } }
        Disposer.register(testRootDisposable, cache)
        project.replaceService(DefinitionModulePresence::class.java, cache, testRootDisposable)
        refresherWatch.reset()
    }

    /** What the real refresher owes the test after the changes since the last wait (see [RefresherWatch]). */
    private val refresherWatch by lazy { RefresherWatch(project, { definition }, { violations }) }

    protected fun sourceFile(): VirtualFile =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourcePath) ?: error("$sourcePath was not found")

    /** Opens the source file in the editor, as it was last written at [fileWrittenAt]. */
    protected fun open(): PsiFile {
        myFixture.configureFromExistingVirtualFile(sourceFile())
        // The fixture writes the file once more as it opens it: the user's last write is still [fileWrittenAt].
        Files.setLastModifiedTime(sourcePath, FileTime.from(fileWrittenAt))
        sourceFile().refresh(false, false)
        return myFixture.file
    }

    /** Waits until [condition] holds, with the EDT running. */
    protected fun waitUntil(what: String, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching({ what }, condition, 10)
    }

    /** Waits [millis] with the EDT running. */
    protected fun idle(millis: Long) {
        val until = System.currentTimeMillis() + millis
        PlatformTestUtil.waitWithEventsDispatching({ "" }, { System.currentTimeMillis() >= until }, 10)
    }

    /**
     * The katachi ◆ of the open file after the daemon ran again. The test restarts the daemon itself (an unchanged file is not
     * recomputed by the daemon); [redrawnByRefresher] leaves that to the real [HighlightingRefresher].
     */
    protected fun redrawn(): List<DrawnMark> {
        // What the test draws by itself it has looked at: the refresher owes what changes from here on, also while this draw runs
        // (a load that ends during it is a change the refresher must draw, which the next wait is for).
        refresherWatch.reset()
        DaemonCodeAnalyzer.getInstance(project).restart(myFixture.file, "katachi test: the answer changed")
        myFixture.doHighlighting()
        return marks()
    }

    /** The ◆ after the real [HighlightingRefresher] drew for the changes since the last call (waited on its own record, not a clock): a ◆ still old means it did not reach the open file. */
    protected fun redrawnByRefresher(): List<DrawnMark> {
        refresherWatch.settle()
        myFixture.doHighlighting()
        return marks()
    }

    private fun katachiMarkers() = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
        .filter { it.lineMarkerTooltip?.startsWith("katachi:") == true }
        .sortedBy { it.startOffset }

    protected fun marks(): List<DrawnMark> = katachiMarkers().map {
        DrawnMark(
            line = myFixture.editor.document.getLineNumber(it.startOffset) + 1,
            leaf = it.element?.text.orEmpty(),
            tooltip = it.lineMarkerTooltip,
            isLeaf = it.element?.firstChild == null,
            hasMenu = it.navigationHandler != null,
        )
    }

    /** The katachi annotations the daemon holds for the open file now. */
    protected fun annotations(): List<DrawnAnnotation> = drawnAnnotations(project, myFixture.file, myFixture.editor.document)

    /** The annotations after the real [HighlightingRefresher] drew for the changes since the last call: one still old means it did not reach the open file. */
    protected fun annotatedByRefresher(): List<DrawnAnnotation> {
        refresherWatch.settle()
        myFixture.doHighlighting()
        return annotations()
    }

    /** The menu of the one ◆ the daemon drew. */
    protected fun menu(): GutterMenu = katachiMarkers().mapNotNull { it.navigationHandler as? GutterMenu }.single()

    /** Types at the end of the open file, without saving. */
    protected fun typeAtEnd(text: String) = WriteCommandAction.runWriteCommandAction(project) {
        myFixture.editor.document.insertString(myFixture.editor.document.textLength, text)
    }

    /** Writes the Documents to disk, as the user's save does. */
    protected fun save() = WriteCommandAction.runWriteCommandAction(project) { FileDocumentManager.getInstance().saveAllDocuments() }

    companion object {
        const val SOURCE = "src/service/HealthService.kt"
    }
}
