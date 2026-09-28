package me.tbsten.katachi.intellij.ide.dialog

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogIntent
import me.tbsten.katachi.intellij.testing.underRoot

/**
 * The dialog's frame, created but never shown (headless: the Compose content is not composed, spike
 * S2 (e)). The content is covered by the Composable's own tests and previews; here the frame is: the
 * title, the OK button following the ViewModel, what OK hands over and what it does not, Esc.
 */
internal class GenerateDialogWrapperTest : AnalysisTestBase() {
    private val world = DialogWorld()

    private fun <T> withDialog(block: (KatachiGenerateDialog) -> T): T {
        val dialog = world.dialog(project)
        try {
            return block(dialog)
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    private fun KatachiGenerateDialog.type(name: String, value: String) = viewModel.dispatch(GenerateDialogIntent.Input(name, value))

    // covers: 論点17
    fun `test 見出しは英語が既定で生成ボタンを持つ`() {
        val title = withDialog { it.title }

        assertEquals(KatachiBundle.message("dialog.title"), title)
        assertEquals("katachi: Generate from Template", title)
    }

    // covers: 論点0
    fun `test 開いたときの初期値は要求のテンプレートと決まる capture に一致する`() {
        withDialog { dialog ->
            val state = dialog.viewModel.state.value
            assertEquals(world.request.initialTemplate, state.selectedTemplate)
            assertEquals(world.request.seeds, state.fields.filter { it.seeded }.associate { it.name to it.value })
            assertNull(dialog.result)
            assertFalse("not closed with OK", dialog.exitCode == DialogWrapper.OK_EXIT_CODE)
        }
    }

    // covers: 論点1
    fun `test 決まらない必須項目が空のあいだは生成できず埋めると生成できる`() {
        withDialog { dialog ->
            assertFalse(dialog.isOKActionEnabled)

            dialog.type("name", "Profile")
            assertFalse("title (a required parameter) is still empty", dialog.isOKActionEnabled)

            dialog.type("title", "Profile")
            assertTrue(dialog.isOKActionEnabled)

            dialog.type("name", "")
            assertFalse(dialog.isOKActionEnabled)
        }
    }

    // covers: 論点2
    fun `test 生成を押すと入力どおりの要求を渡して閉じる`() {
        withDialog { dialog ->
            dialog.type("name", "Profile")
            dialog.type("title", "Profile")

            dialog.pressGenerate()

            val result = dialog.result
            assertNotNull(result)
            assertEquals(world.screen.id, result!!.template.id)
            assertEquals(world.request.origin, result.origin)
            assertEquals(underRoot("feature/home/ui/ProfileScreen.kt"), result.target)
            assertEquals(listOf("feature" to "home", "name" to "Profile", "title" to "Profile"), result.args.sortedBy { it.first })
            assertEquals(DialogWrapper.OK_EXIT_CODE, dialog.exitCode)
        }
    }

    // covers: 論点6
    fun `test 表示のあとで出力先に中身が書かれていたら閉じずに理由を出し何も渡さない`() {
        withDialog { dialog ->
            dialog.type("name", "Profile")
            dialog.type("title", "Profile")
            assertTrue("the notice still says the file is free", dialog.isOKActionEnabled)
            world.existing[underRoot("feature/home/ui/ProfileScreen.kt")] = TargetState.HasContent

            dialog.pressGenerate()

            assertNull(dialog.result)
            assertFalse("not closed with OK", dialog.exitCode == DialogWrapper.OK_EXIT_CODE)
            assertEquals(KatachiBundle.message("dialog.generateRefused", KatachiBundle.message("dialog.target.hasContent")), dialog.refusal)
        }
    }

    // covers: 論点6
    fun `test 理由を出したあと別の出力先に直すと生成できる`() {
        withDialog { dialog ->
            dialog.type("name", "Profile")
            dialog.type("title", "Profile")
            world.existing[underRoot("feature/home/ui/ProfileScreen.kt")] = TargetState.HasContent
            dialog.pressGenerate()
            assertNotNull(dialog.refusal)

            dialog.type("name", "Detail")
            dialog.pressGenerate()

            assertEquals(underRoot("feature/home/ui/DetailScreen.kt"), dialog.result?.target)
            assertEquals(DialogWrapper.OK_EXIT_CODE, dialog.exitCode)
        }
    }

    // covers: 論点0
    fun `test Esc相当のキャンセルでは何も渡さず確かめもしない`() {
        withDialog { dialog ->
            dialog.type("name", "Profile")
            dialog.type("title", "Profile")
            val checksBefore = world.checks.size

            dialog.pressCancel()

            assertNull(dialog.result)
            assertEquals(DialogWrapper.CANCEL_EXIT_CODE, dialog.exitCode)
            assertEquals("Esc must not ask the target again", checksBefore, world.checks.size)
        }
    }

    // covers: 論点17
    fun `test 生成できないあいだに生成を押しても閉じない`() {
        withDialog { dialog ->
            dialog.pressGenerate()

            assertNull(dialog.result)
            assertFalse(dialog.isDisposed)
            assertFalse("not closed with OK", dialog.exitCode == DialogWrapper.OK_EXIT_CODE)
        }
    }
}
