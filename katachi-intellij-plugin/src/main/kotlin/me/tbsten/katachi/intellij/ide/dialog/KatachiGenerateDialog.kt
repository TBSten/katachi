package me.tbsten.katachi.intellij.ide.dialog

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogIntent
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogViewModel
import me.tbsten.katachi.intellij.presentation.dialog.dialogUiStateOf
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogActions
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogContent
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext

/**
 * The frame of the generate dialog: a resizable [DialogWrapper] that hosts the shared Composable
 * (`GenerateDialogContent`) and whose OK button is [Generate]. The behaviour is the ViewModel's
 * (`GenerateDialogViewModel`); this class only connects it to the IDE:
 *
 * - The OK button follows the ViewModel's `canGenerate`; the title and buttons are worded from [strings].
 * - **[Generate]** asks the same existing-file check the ViewModel's notice uses. A target with content
 *   keeps the dialog open and says why (issue 6); anything else closes it with [result] set, and the
 *   caller runs the generation (the dialog writes nothing).
 * - **Esc**, the close button and Cancel close the dialog with no [result]: nothing is written.
 * - The dialog follows the shared list (`templateChanges`) and files written outside (`filesChanged`)
 *   while it is open.
 *
 * Coroutines run with `ModalityState.any()`: this dialog is modal, and a continuation of the default
 * modality would wait until it closes.
 *
 * ```kotlin
 * val dialog = KatachiGenerateDialog(project, environment)
 * dialog.show()
 * dialog.result // the request to generate, or null when cancelled
 * ```
 */
internal class KatachiGenerateDialog(
    project: Project,
    private val environment: GenerateDialogEnvironment,
    private val strings: GenerateDialogStrings = BundleGenerateDialogStrings,
    uiContext: CoroutineContext = Dispatchers.EDT + ModalityState.any().asContextElement(),
    private val checkContext: CoroutineContext = Dispatchers.Default,
    settle: suspend () -> Unit = { delay(GenerateDialogViewModel.CHECK_DEBOUNCE_MILLIS) },
) : DialogWrapper(project) {
    private val scope = CoroutineScope(SupervisorJob() + uiContext)

    val viewModel: GenerateDialogViewModel = GenerateDialogViewModel(
        scope = scope,
        request = environment.request,
        candidates = environment.candidates,
        seeds = environment.seeds,
        checkTarget = environment.checkTarget,
        rootOf = environment.rootOf,
        checkContext = checkContext,
        settle = settle,
    )

    private val refusalState = MutableStateFlow<String?>(null)
    private var confirming: Job? = null

    /** Why [Generate] stopped without closing, as the dialog shows it; `null` when it did not. */
    val refusal: String? get() = refusalState.value

    /** What to generate once the dialog closed with OK; `null` while open and after any other way out. */
    var result: SingleFileGenerationRequest? = null
        private set

    private val actions = object : GenerateDialogActions {
        override fun onSelectTemplate(index: Int) {
            viewModel.state.value.candidates.getOrNull(index)?.let { changed(GenerateDialogIntent.SelectTemplate(it.id)) }
        }

        override fun onSelectDefinition(index: Int) {
            viewModel.state.value.definitions.getOrNull(index)?.let { changed(GenerateDialogIntent.SelectDefinition(it.id)) }
        }

        override fun onInput(name: String, value: String) = changed(GenerateDialogIntent.Input(name, value))

        override fun onGenerate() = doOKAction()
    }

    init {
        title = strings.title
        setOKButtonText(strings.generate)
        isOKActionEnabled = viewModel.state.value.canGenerate
        Disposer.register(disposable) { scope.cancel() }
        follow()
        init()
    }

    private fun changed(intent: GenerateDialogIntent) {
        refusalState.value = null
        viewModel.dispatch(intent)
    }

    /** Keeps the OK button, the template list and the target check in step with the outside. */
    private fun follow() {
        scope.launch { viewModel.state.collect { isOKActionEnabled = it.canGenerate } }
        var offered = environment.candidates
        scope.launch {
            // A failing flow of the platform must not take the dialog down; control flow still propagates.
            sdkCall("follow the template list") {
                environment.templateChanges.collect { templates ->
                    if (templates != offered) {
                        offered = templates
                        viewModel.dispatch(GenerateDialogIntent.ListChanged(templates))
                    }
                }
            }
        }
        scope.launch {
            sdkCall("follow the files written outside the dialog") {
                environment.filesChanged.collect { viewModel.dispatch(GenerateDialogIntent.FilesChangedOutside) }
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JewelComposePanel {
            val state by viewModel.state.collectAsState()
            val refused by refusalState.collectAsState()
            val ui = remember(state, refused) { dialogUiStateOf(state, strings, refused) }
            GenerateDialogContent(ui, strings, actions)
        }
        panel.preferredSize = Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT)
        panel.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
        return panel
    }

    override fun getDimensionServiceKey(): String = "me.tbsten.katachi.GenerateDialog"

    /**
     * [Generate]. Checks the target once more (the notice may be stale: typing is checked after a pause),
     * and closes only when it does not have content. A second press while checking does nothing.
     */
    override fun doOKAction() {
        if (!isOKActionEnabled || confirming?.isActive == true) return
        val request = viewModel.generationRequest() ?: return
        refusalState.value = null
        confirming = scope.launch {
            val target = withContext(checkContext) { sdkCall("check the target before generating") { environment.checkTarget(request.target) }.getOrNull() }
            if (target == TargetState.HasContent) {
                refusalState.value = strings.generateRefused(strings.targetHasContent)
                return@launch
            }
            // Unknown (the check failed): the generation checks again before writing and says why it stops.
            result = request
            closeWithOk()
        }
    }

    private fun closeWithOk() = super.doOKAction()

    /** Presses [Generate], for tests that cannot click a modal dialog. */
    @TestOnly
    fun pressGenerate() = doOKAction()

    /** Presses Cancel (what Esc does). */
    @TestOnly
    fun pressCancel() = doCancelAction()

    private companion object {
        // D2's window sizes (min / standard).
        const val PREFERRED_WIDTH = 640
        const val PREFERRED_HEIGHT = 480
        const val MIN_WIDTH = 480
        const val MIN_HEIGHT = 380
    }
}
