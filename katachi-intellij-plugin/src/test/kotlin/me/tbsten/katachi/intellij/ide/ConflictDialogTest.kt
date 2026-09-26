package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId
import java.nio.file.Path

internal class ConflictDialogTest : AnalysisTestBase() {
    private val root = Path.of("/work/project")
    private val question = ConflictQuestion(
        templateId = TemplateId(ModuleId(root, ":arch-a"), "data/Repository"),
        index = 1,
        total = 3,
        existing = listOf(root.resolve("data/src/main/kotlin/com/example/data/UserRepository.kt")),
    )

    private fun <T> withDialog(block: (ConflictDialog) -> T): T {
        val dialog = ConflictDialog(project, question, emptyList())
        try {
            return block(dialog)
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    fun `test 上書きを選んで続けると Overwrite になる`() {
        val choice = withDialog { dialog ->
            dialog.continueWith(overwrite = true)
            dialog.choice
        }
        assertEquals(ConflictChoice.Overwrite, choice)
    }

    fun `test 書かずに次へを選んで続けると SkipAndContinue になる`() {
        val choice = withDialog { dialog ->
            dialog.continueWith(overwrite = false)
            dialog.choice
        }
        assertEquals(ConflictChoice.SkipAndContinue, choice)
    }

    fun `test ここで止めるか閉じると Stop になる`() {
        val choice = withDialog { dialog ->
            dialog.stop()
            dialog.choice
        }
        assertEquals(ConflictChoice.Stop, choice)
    }

    fun `test 見出しに何件目のどのテンプレートかを出す`() {
        val title = withDialog { it.title }
        assertEquals(KatachiBundle.message("conflict.title", 2, 3, "Repository"), title)
    }

    fun `test テスト用の答えを差し込むとダイアログを出さずにその答えを返す`() {
        ConflictDialogs.setTestAnswer({ ConflictChoice.SkipAndContinue }, testRootDisposable)

        assertEquals(ConflictChoice.SkipAndContinue, ConflictDialogs.ask(project, question, emptyList()))
    }

    fun `test ラジオボタンを触らずに続けると上書きせず書かずに次へになる`() {
        val choice = withDialog { dialog ->
            dialog.close(DialogWrapper.OK_EXIT_CODE)
            dialog.choice
        }
        assertEquals(ConflictChoice.SkipAndContinue, choice)
    }
}
