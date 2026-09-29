package me.tbsten.katachi.intellij.ide.dialog

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogIntent

/**
 * The dialog closing with [Generate] under the real EDT dispatcher, as in the IDE (V1 2.1): closing
 * disposes the dialog and cancels its coroutines, the one that closes it included. The platform's own
 * dispose checks cancellation (saving the window size), so it must not run where a cancelled job throws;
 * in the IDE that was a red "CE must not be thrown from a dispose()" after every [Generate].
 */
internal class GenerateDialogCloseTest : AnalysisTestBase() {
    private val world = DialogWorld()

    /** What a dispose of the dialog sees: the coroutine job it runs under, and whether cancellation is held off. */
    private data class AtDispose(val job: Job?, val nonCancelable: Boolean)

    // covers: 論点17
    fun `test 生成で閉じるときの後始末は取り消される仕事の中で取り消しを確かめない`() {
        val dialog = KatachiGenerateDialog(
            project,
            world.environment(),
            uiContext = Dispatchers.EDT + ModalityState.any().asContextElement(),
            checkContext = Dispatchers.Unconfined,
            settle = {},
        )
        val seen = mutableListOf<AtDispose>()
        Disposer.register(dialog.disposable) {
            seen += AtDispose(Cancellation.currentJob(), ProgressManager.getInstance().isInNonCancelableSection)
        }
        try {
            dialog.viewModel.dispatch(GenerateDialogIntent.Input("name", "Profile"))
            dialog.viewModel.dispatch(GenerateDialogIntent.Input("title", "Profile"))
            PlatformTestUtil.waitWithEventsDispatching("the Generate button is enabled", { dialog.isOKActionEnabled }, 10)

            dialog.pressGenerate()
            PlatformTestUtil.waitWithEventsDispatching("the dialog closes", { dialog.isDisposed }, 10)
        } finally {
            if (!dialog.isDisposed) Disposer.dispose(dialog.disposable)
        }

        assertEquals(DialogWrapper.OK_EXIT_CODE, dialog.exitCode)
        assertNotNull(dialog.result)
        val atDispose = seen.single()
        assertTrue(
            "the dispose ran under the [Generate] coroutine's job, which the close itself cancels: $atDispose",
            atDispose.job == null || atDispose.nonCancelable,
        )
    }
}
