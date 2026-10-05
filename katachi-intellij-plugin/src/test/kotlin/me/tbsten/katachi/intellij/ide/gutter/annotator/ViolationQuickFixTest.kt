package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterMenu
import me.tbsten.katachi.intellij.ide.gutter.RecordingAction
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import java.nio.file.Files

/** V2 of the gutter's P3: what Alt+Enter offers on a violation (Go to the rule, Run the check again) and what choosing each does. */
internal class ViolationQuickFixTest : AnnotatorTestBase() {
    private val fileName = "HealthService.kt"
    private val path get() = pathOf(fileName)
    private val source = "package a\n\ninternal class HealthService\n"
    private val siteOfRule = DeclarationSiteRef("ServiceRole.kt", 94)

    private fun roleFile(): VirtualFile = myFixture.addFileToProject("ServiceRole.kt", (1..100).joinToString("\n") { "// line $it" }).virtualFile

    private fun fixes(description: String): List<IntentionAction> = fixesOf(infoWith(description))

    private fun fix(description: String, text: String): IntentionAction =
        fixes(description).firstOrNull { it.text == text } ?: throw AssertionError("No fix \"$text\" on \"$description\": ${fixes(description).map { it.text }}")

    private fun rejected() {
        open(fileName, source)
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3)))
        annotated()
    }

    fun `test 定義モジュールが宣言元を見つけたら 規則へ移動と 検査をやり直す が この順で出る`() {
        definitions.resolveWith(siteOfRule, ResolvedSite.Found(roleFile(), 94))
        rejected()
        assertEquals(listOf("Go to the rule (ServiceRole.kt:94)", "Run the check again"), fixes("rejected HealthService").map { it.text })
    }

    fun `test 宣言元が見つからないときは 規則へ移動は出さず 検査をやり直すだけ出る`() {
        rejected()
        assertEquals(listOf("Run the check again"), fixes("rejected HealthService").map { it.text })
    }

    fun `test 報告が関連する場所を持つときは その場所を規則の場所にする`() {
        open(fileName, source)
        val related = SarifLocation(pathOf("ServiceRole.kt"), 94)
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3, related = listOf(related))))
        annotated()
        assertEquals(listOf("Go to the rule (ServiceRole.kt:94)", "Run the check again"), fixes("rejected HealthService").map { it.text })
    }

    fun `test 規則へ移動を選ぶと 定義の規則の行が開く`() {
        val role = roleFile()
        definitions.resolveWith(siteOfRule, ResolvedSite.Found(role, 94))
        rejected()
        val goTo = fix("rejected HealthService", "Go to the rule (ServiceRole.kt:94)")

        goTo.invoke(project, myFixture.editor, myFixture.file)

        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: throw AssertionError("nothing was opened")
        assertEquals(role, FileEditorManager.getInstance(project).selectedFiles.single())
        assertEquals(93, editor.caretModel.logicalPosition.line)
        assertEquals(emptyList<String>(), balloons)
    }

    fun `test 報告の場所のファイルが無いときは 開かず 見つからないと知らせる`() {
        useLocalFiles()
        open(fileName, source)
        val related = SarifLocation(lightPath("Missing.kt"), 5)
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3, related = listOf(related))))
        annotated()

        fix("rejected HealthService", "Go to the rule (Missing.kt:5)").invoke(project, myFixture.editor, myFixture.file)

        assertEquals(listOf("katachi: Missing.kt is not found. The definition may be stale."), balloons)
    }

    fun `test 報告の場所のファイルがあれば その行が開く`() {
        useLocalFiles()
        val role = lightPath("ServiceRole.kt")
        Files.writeString(role, (1..100).joinToString("\n") { "// line $it" })
        open(fileName, source)
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3, related = listOf(SarifLocation(role, 94)))))
        annotated()

        fix("rejected HealthService", "Go to the rule (ServiceRole.kt:94)").invoke(project, myFixture.editor, myFixture.file)

        assertEquals("ServiceRole.kt", FileEditorManager.getInstance(project).selectedFiles.single().name)
        assertEquals(93, FileEditorManager.getInstance(project).selectedTextEditor?.caretModel?.logicalPosition?.line)
    }

    fun `test 検査をやり直すを選ぶと 土台の RunCheck が このファイルと project で実行される`() {
        val runCheck = RecordingAction("run")
        actions[GutterMenu.RUN_CHECK] = runCheck
        rejected()
        val sourceFile = myFixture.file.virtualFile

        fix("rejected HealthService", "Run the check again").invoke(project, myFixture.editor, myFixture.file)

        val context = runCheck.events.single().dataContext
        assertEquals(sourceFile, context.getData(CommonDataKeys.VIRTUAL_FILE))
        assertEquals(project, context.getData(CommonDataKeys.PROJECT))
    }

    fun `test 検査をやり直すが失敗したときの知らせは ガターのメニューではなく 直しの言葉で言う`() {
        actions[GutterMenu.RUN_CHECK] = RecordingAction("run") { throw IllegalStateException("boom") }
        rejected()

        fix("rejected HealthService", "Run the check again").invoke(project, myFixture.editor, myFixture.file)

        assertEquals(listOf("katachi: The quick fix could not do that: boom"), balloons)
    }

    fun `test RunCheck が登録されていない間は 検査をやり直すは使えない`() {
        rejected()
        assertFalse(fix("rejected HealthService", "Run the check again").isAvailable(project, myFixture.editor, myFixture.file))
        actions[GutterMenu.RUN_CHECK] = RecordingAction("run")
        assertTrue(fix("rejected HealthService", "Run the check again").isAvailable(project, myFixture.editor, myFixture.file))
    }

    fun `test ファイルの上の注釈にも 検査をやり直すが付き 規則が分かれば 規則へ移動も付く`() {
        open(fileName, source)
        definitions.resolveWith(siteOfRule, ResolvedSite.Found(roleFile(), 94))
        checkRan(listOf(AnnotationFixtures.missing(path, declaredAt = AnnotationFixtures.RULE_AT)))
        annotated()
        assertEquals(listOf("Go to the rule (ServiceRole.kt:94)", "Run the check again"), fixes("Missing: class").map { it.text })
    }
}
