package me.tbsten.katachi.intellij.ide.dialog

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import org.jetbrains.annotations.TestOnly

/**
 * Shows [KatachiGenerateDialog], or answers with the test hook when one is set, as `ConflictDialogs`
 * does: a modal dialog cannot be clicked in a headless test.
 *
 * ```kotlin
 * val generation: SingleFileGenerationRequest? = GenerateDialogs.open(project, request) { environment }.getOrNull()
 * ```
 */
internal object GenerateDialogs {
    @Volatile private var testAnswer: ((GenerateDialogRequest) -> SingleFileGenerationRequest?)? = null

    /** The dialog on screen, for `KatachiDebugBridge.describeDialog`; there is one at a time (it is modal). */
    @Volatile var showing: KatachiGenerateDialog? = null
        private set

    /**
     * Call on the EDT. What the user asked to generate, or `null` when they cancelled. A dialog the IDE
     * fails to build or show is a failed [Result] (the caller tells the user); control flow is thrown again.
     * [environment] is read inside the guarded call, so failing to read the project fails the same way.
     */
    fun open(
        project: Project,
        request: GenerateDialogRequest,
        environment: () -> GenerateDialogEnvironment,
    ): Result<SingleFileGenerationRequest?> {
        testAnswer?.let { return Result.success(it(request)) }
        return sdkCall("show the generate dialog for ${request.initialTemplate.template}") {
            val dialog = KatachiGenerateDialog(project, environment())
            showing = dialog
            try {
                dialog.show()
                dialog.result
            } finally {
                showing = null
            }
        }
    }

    @TestOnly
    fun setTestAnswer(answer: (GenerateDialogRequest) -> SingleFileGenerationRequest?, parentDisposable: Disposable) {
        testAnswer = answer
        Disposer.register(parentDisposable) { testAnswer = null }
    }

    /** Pretends [dialog] is on screen, for the debug bridge's test. */
    @TestOnly
    fun setShowingForTest(dialog: KatachiGenerateDialog, parentDisposable: Disposable) {
        showing = dialog
        Disposer.register(parentDisposable) { showing = null }
    }
}
