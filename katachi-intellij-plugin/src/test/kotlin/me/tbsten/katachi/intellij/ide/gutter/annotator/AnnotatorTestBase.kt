package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.ide.gutter.GutterTestBase
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Platform tests of [KatachiViolationAnnotator] over light files and the foundation's fakes: the annotator is registered for
 * Kotlin with ports that answer from the fake violations, the real PSI and daemon draw it. [GutterTestBase] gives the world
 * (the ports, the files); this adds the annotator and what a check leaves.
 */
internal abstract class AnnotatorTestBase : GutterTestBase() {
    lateinit var annotator: KatachiViolationAnnotator

    override fun setUp() {
        super.setUp()
        annotator = KatachiViolationAnnotator { ports() }
        ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(KotlinLanguage.INSTANCE, annotator, testRootDisposable)
    }

    /** Where the violations of [fileName] point. */
    fun pathOf(fileName: String): Path = lightPath(fileName)

    private var checks = 0L

    /** A check ended with [results]: the SARIF was read. [stale]: the project changed after it. */
    fun checkRan(results: List<ViolationModel>, stale: Boolean = false) {
        violations.emit(ViolationsState.Ready(results, DEFINITION_INSTANT.plusSeconds(++checks), stale, listOf(LIGHT_SARIF)))
    }

    /** The annotations after the daemon ran again; the test restarts it, which stands for [HighlightingRefresher]. */
    fun annotated(): List<DrawnAnnotation> {
        DaemonCodeAnalyzer.getInstance(project).restart(myFixture.file, "katachi test: the violations changed")
        myFixture.doHighlighting()
        return annotationsDrawn()
    }

    /** The annotations after the real [HighlightingRefresher] drew for the changes since the last call; the test never restarts the daemon. */
    fun annotatedByRefresher(): List<DrawnAnnotation> {
        refresherWatch.settle()
        myFixture.doHighlighting()
        return annotationsDrawn()
    }

    /** Waits [millis] with the EDT running. */
    fun idleFor(millis: Long) {
        val until = System.currentTimeMillis() + millis
        PlatformTestUtil.waitWithEventsDispatching("settle", { System.currentTimeMillis() >= until }, 5)
    }

    private fun annotationsDrawn(): List<DrawnAnnotation> = drawnAnnotations(project, myFixture.file, myFixture.editor.document)

    /** The quick fixes registered on [info], in the order the Alt+Enter menu lists them. */
    fun fixesOf(info: HighlightInfo): List<IntentionAction> = quickFixesOf(info)

    /** The daemon's [HighlightInfo] of the katachi annotation that says [description]. */
    fun infoWith(description: String): HighlightInfo {
        val infos = allHighlights(project, myFixture.file, myFixture.editor.document)
        return infos.firstOrNull { it.description?.contains(description) == true }
            ?: throw AssertionError("No annotation says \"$description\": ${infos.map { it.description }}")
    }

    companion object {
        /** The SARIF the fake pretends to have read. */
        val LIGHT_SARIF: Path = Paths.get("/build/reports/katachi/katachi.sarif")
    }
}
