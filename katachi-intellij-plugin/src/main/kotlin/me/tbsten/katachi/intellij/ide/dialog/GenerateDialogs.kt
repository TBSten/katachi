package me.tbsten.katachi.intellij.ide.dialog

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import org.jetbrains.annotations.TestOnly

internal object GenerateDialogs {
    @Volatile private var testAnswer: ((GenerateDialogRequest) -> SingleFileGenerationRequest?)? = null

    @Volatile var showing: KatachiGenerateDialog? = null
        private set

    // Call on the EDT.
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

    @TestOnly
    fun setShowingForTest(dialog: KatachiGenerateDialog, parentDisposable: Disposable) {
        showing = dialog
        Disposer.register(parentDisposable) { showing = null }
    }
}
