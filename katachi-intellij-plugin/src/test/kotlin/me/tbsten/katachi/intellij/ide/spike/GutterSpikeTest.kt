package me.tbsten.katachi.intellij.ide.spike

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.lang.Language
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThrowableRunnable
import me.tbsten.katachi.intellij.AnalysisTestBase
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The two spikes of the gutter's P1 (plan.md of ide-gutter): (a) whether a line marker provider registered for
 * `Language.ANY` (what `language=""` in plugin.xml is) is asked for plain text, XML, Markdown and Kotlin script
 * files, and (b) whether an [ExternalAnnotator] runs under `doHighlighting()` and is given `hasErrors = true` in
 * its three-argument `collectInformation` for a file with a compile error. The results are in background.md F-5.
 *
 * Not here: dumb mode. `doHighlighting()` inside `DumbModeTestUtils` spins on the EDT without returning (the test
 * daemon waits for smart mode), so the features' tests call their provider and annotator directly in dumb mode.
 */
internal class GutterSpikeTest : AnalysisTestBase() {
    // configureByText of some file types starts bundled plugins (the Vue LSP) that log errors in this IDE build.
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        ignoreUnrelatedLoggedErrors { super.runTestRunnable(testRunnable) }
    }

    // --- (a) language="" ---

    private class AnyLanguageProvider : LineMarkerProvider, DumbAware {
        val files = CopyOnWriteArrayList<String>()

        override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

        override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
            val file = elements.firstOrNull()?.containingFile ?: return
            files += "${file.name}:${file.language.id}"
            // A Markdown file has a second PSI root (HTML) and is asked once per root: draw on the base language's only.
            if (file.language != file.viewProvider.baseLanguage) return
            // The pass asks once for the elements in the visible range and once for the rest: draw only when the leaf is among these.
            val first = generateSequence(PsiTreeUtil.firstChild(file)) { PsiTreeUtil.nextLeaf(it) }
                .firstOrNull { it !is PsiWhiteSpace && it.textLength > 0 } ?: return
            if (elements.none { it === first }) return
            result += LineMarkerInfo(first, first.textRange, ICON, { "spike" }, null, GutterIconRenderer.Alignment.LEFT) { "spike" }
        }
    }

    private fun gutterTooltipsOf(fileName: String, text: String, provider: AnyLanguageProvider): List<String?> {
        myFixture.configureByText(fileName, text)
        return myFixture.findAllGutters().filter { it.tooltipText == "spike" }.map { it.tooltipText }.also {
            spike("a.$fileName", "language=${myFixture.file.language.id} asked=${provider.files} gutters=${it.size}")
        }
    }

    fun `test a provider registered for Language ANY is asked for files of every language`() {
        val provider = AnyLanguageProvider()
        // The key of Language.ANY is its ID, "": the same key plugin.xml's language="" registers under.
        LineMarkerProviders.getInstance().addExplicitExtension(Language.ANY, provider, testRootDisposable)
        spike("a.anyId", "'${Language.ANY.id}'")

        val results = linkedMapOf(
            "notes.txt" to "first line\nsecond line\n",
            "README.md" to "# Title\n\ntext\n",
            "build.gradle.kts" to "plugins {\n    kotlin(\"jvm\")\n}\n",
            "gradle.properties" to "a=b\n",
            "libs.toml" to "[versions]\na = \"1\"\n",
            "Main.kt" to "package a\n\nclass A\n",
            // Not XML: highlighting an XML file starts the bundled Vue LSP support, whose class fails to initialize in this
            // test IDE, throws out of findAllGutters and fails the next platform test too (the provider was asked meanwhile).
        ).mapValues { (name, text) ->
            runCatching { gutterTooltipsOf(name, text, provider).size }
                .getOrElse { failure -> spike("a.$name.failed", "${failure.javaClass.name}: ${failure.message} asked=${provider.files}"); -1 }
        }

        spike("a.results", results)
        // build.gradle.kts: the light test project has no script configuration, so ProblemHighlightFilter refuses to
        // highlight it at all. That is the test environment's, not the provider's.
        val runnable = results.filterKeys { it != "build.gradle.kts" }
        assertEquals("one gutter at the start of every file: $results", runnable.keys.associateWith { 1 }, runnable)
    }

    // --- (b) ExternalAnnotator ---

    private class RecordingAnnotator : ExternalAnnotator<String, String>(), DumbAware {
        val calls = CopyOnWriteArrayList<String>()

        override fun collectInformation(file: PsiFile): String? {
            calls += "collect1"
            return file.name
        }

        override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): String? {
            calls += "collect3(hasErrors=$hasErrors)"
            return file.name
        }

        override fun doAnnotate(collectedInfo: String?): String? {
            calls += "doAnnotate"
            return collectedInfo
        }

        override fun apply(file: PsiFile, annotationResult: String?, holder: AnnotationHolder) {
            calls += "apply"
            holder.newAnnotation(HighlightSeverity.WARNING, "spike-file-level").fileLevel().create()
            holder.newAnnotation(HighlightSeverity.WARNING, "spike-range").range(file.textRange).create()
        }
    }

    private fun annotate(text: String): Pair<List<String>, List<String>> {
        val annotator = RecordingAnnotator()
        ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(KotlinLanguage.INSTANCE, annotator, testRootDisposable)
        myFixture.configureByText("Spike.kt", text)
        val highlights = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("spike") }
        return annotator.calls.toList() to highlights
    }

    fun `test an external annotator runs under doHighlighting for a file without errors`() {
        val (calls, highlights) = annotate("package a\n\nclass A\n")
        spike("b.clean", "calls=$calls highlights=$highlights")
        assertTrue(calls.toString(), "collect3(hasErrors=false)" in calls)
        assertTrue(highlights.toString(), "spike-range" in highlights)
    }

    fun `test an external annotator is given hasErrors true for a file with a compile error`() {
        val (calls, highlights) = annotate("package a\n\nclass A {\n    fun f( {\n}\n")
        spike("b.errors", "calls=$calls highlights=$highlights")
        assertTrue(calls.toString(), "collect3(hasErrors=true)" in calls)
        assertTrue(highlights.toString(), "spike-range" in highlights)
    }

    private companion object {
        val ICON = IconLoader.getIcon("/icons/katachi.svg", GutterSpikeTest::class.java)
    }
}
