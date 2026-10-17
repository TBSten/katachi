package me.tbsten.katachi.intellij.ide.authoring.errors

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.DumbModeTestUtils
import me.tbsten.katachi.intellij.ide.definition.RefreshReason
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent
import me.tbsten.katachi.intellij.model.DefinitionProblem
import me.tbsten.katachi.intellij.model.DefinitionProblemKind
import java.util.concurrent.TimeUnit

/** F-6, F-7, F-8, F-14 of ide-authoring: the mistakes and problems of a definition as the real daemon draws them in the definition file. */
internal class DefinitionErrorAnnotatorTest : DefinitionErrorTestBase() {
    fun `test 誤りの行に error の下線が付き ホバーは改行つきの全文 quick fix は 読み直しと全文`() {
        openDefinition()
        rejected(mistake(4))

        val drawn = annotated().single()

        assertEquals(HighlightSeverity.ERROR, drawn.severity)
        assertEquals(4, drawn.line)
        assertEquals("// line 4", drawn.text)
        assertEquals("katachi: `.template { }` at ScreenRole.kt:4 is attached to a wildcard.\nUse a name.", drawn.description)
        assertTrue("the hover is HTML with the lines kept: ${drawn.tooltip}", drawn.tooltip.orEmpty().contains("<br>"))
        assertEquals(listOf("Reload Definition", "Show the whole message"), drawn.fixes)
    }

    fun `test 変更の後は weak warning と stale の印に下がり 次の読み込みが成功すると消える`() {
        openDefinition()
        rejected(mistake(4))
        annotated()

        definitions.emitErrors(DefinitionErrorsEvent.DefinitionChanged)
        val stale = annotated().single()
        assertEquals(HighlightSeverity.WEAK_WARNING, stale.severity)
        assertTrue(stale.description, stale.description.startsWith("(stale) katachi: "))

        definitions.emitErrors(DefinitionErrorsEvent.LoadOk(tick()))
        assertEquals(emptyList<Any>(), annotated())
    }

    fun `test Reload Definition を選ぶと 定義を読み直す依頼が 利用者の操作として 1 回出る`() {
        openDefinition()
        rejected(mistake(4))
        annotated()

        fixesOn("katachi: ").first { it.text == "Reload Definition" }.invoke(project, myFixture.editor, myFixture.file)

        assertEquals(listOf(RefreshReason.UserAction), definitions.refreshes)
    }

    fun `test Show the whole message を選ぶと 誤りの全文が出る`() {
        openDefinition()
        rejected(mistake(4, message = "whole\nmessage\nof the error"))
        annotated()

        fixesOn("katachi: ").first { it.text == "Show the whole message" }.invoke(project, myFixture.editor, myFixture.file)

        assertEquals(listOf("whole\nmessage\nof the error"), shown)
    }

    fun `test 定義ファイルにコンパイルエラーを書き込んでも katachi の注釈は残る`() {
        openDefinition()
        rejected(mistake(4))

        typeAtStart("val broken: Int = \"x\"\nfun g( {\n")

        // The mistake names a line of the file as it was read; the two lines typed above it move the text, not the line number.
        val drawn = annotated().filter { it.description.startsWith("katachi: ") }
        assertEquals(1, drawn.size)
        assertEquals(4, drawn.single().line)
    }

    fun `test 場所が別のファイルの誤りと 場所の無い誤りは このファイルに描かない`() {
        openDefinition()
        definitions.resolveWith(DeclarationSiteRef("Other.kt", 3), me.tbsten.katachi.intellij.ide.definition.ResolvedSite.Unresolved("not here"))
        rejected(mistake(null), mistake(3).copy(site = DeclarationSiteRef("Other.kt", 3)))

        assertEquals(emptyList<Any>(), annotated())
    }

    fun `test 空の行に当たる誤りは 下線を引かない`() {
        openDefinition("// line 1\n\n// line 3\n")
        rejected(mistake(2))

        assertEquals(emptyList<Any>(), annotated())
    }

    fun `test 定義が読めていて template が使うときに落ちるなら その行に warning が付き 古ければ weak warning`() {
        openDefinition()
        readWith(
            DefinitionProblem(DefinitionProblemKind.TemplatePreviewFailed, "TemplatePreviewFailed", "The template could not be previewed.", "ui.Screen", DeclarationSiteRef(SCREEN_ROLE, 6), "java.lang.IllegalStateException"),
            DefinitionProblem(DefinitionProblemKind.TemplateSetPreviewFailed, "TemplateSetPreviewFailed", "The set could not be previewed.", "NewFeature", null, null),
        )

        val drawn = annotated().single()

        assertEquals(HighlightSeverity.WARNING, drawn.severity)
        assertEquals(6, drawn.line)
        assertEquals("katachi: [TemplatePreviewFailed] The template could not be previewed.\nui.Screen", drawn.description)
        assertEquals("a problem is not a mistake: nothing to reload", emptyList<String>(), drawn.fixes)
    }

    fun `test declaredAt の無い TemplatePreviewFailed は その template を書いた layout の行に付く`() {
        openDefinition()
        readWith(
            DefinitionProblem(DefinitionProblemKind.TemplatePreviewFailed, "TemplatePreviewFailed", "The template could not be previewed.", "ui.Screen", null, null),
            templateAt = "ui.Screen" to 9,
        )

        assertEquals(listOf(9), annotated().map { it.line })
    }

    fun `test 誤りも problem も無い定義には 何も描かない`() {
        openDefinition()
        readWith()

        assertEquals(emptyList<Any>(), annotated())
    }

    fun `test 定義モジュールが無いプロジェクトでは 何も描かない`() {
        openDefinition()
        rejected(mistake(4))
        hasDefinitionModule = false

        assertEquals(emptyList<Any>(), annotated())
    }

    fun `test dumb mode でも 注釈を組み立てられ 注釈は DumbAware`() {
        val file = openDefinition()
        rejected(mistake(4))
        assertTrue(DumbService.isDumbAware(annotator))

        // doHighlighting() does not return in dumb mode in the test environment (the daemon waits for smart mode): the annotator is driven directly.
        val output = DumbModeTestUtils.computeInDumbModeSynchronously(project) {
            val input = runReadActionBlocking { annotator.collectInformation(file, myFixture.editor, false) }
            ApplicationManager.getApplication().executeOnPooledThread<DefinitionErrorOutput?> { annotator.doAnnotate(input) }.get(30, TimeUnit.SECONDS)
        } ?: throw AssertionError("nothing was worked out in dumb mode")

        assertEquals(listOf("// line 4"), output.marks.map { myFixture.editor.document.getText(it.range) })
    }
}
