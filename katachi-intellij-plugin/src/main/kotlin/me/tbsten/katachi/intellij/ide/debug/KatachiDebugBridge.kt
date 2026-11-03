package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogs
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import me.tbsten.katachi.intellij.ide.notification.NoRolePanels
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel

@Service(Service.Level.PROJECT)
internal class KatachiDebugBridge(private val project: Project) {
    @TestOnly
    @Volatile
    internal var onScreen: (Component) -> Boolean = { it.isShowing }

    private val viewModel: KatachiToolWindowViewModel
        get() {
            check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
            return KatachiProjectService.getInstance(project).viewModel
        }

    fun describeState(): String {
        val state = viewModel.state.value
        return buildList {
            add("phase=${state.phase.toString().take(PHASE_TEXT_LIMIT)}")
            state.modules.forEach { add("module=${it.gradlePath}") }
            state.rows.forEach { add("template=${it.template.template}") }
            state.generateBlocker?.let { add("blocker=$it") }
            when (val generation = state.generation) {
                null -> Unit
                is GenerationState.Running -> add("generation=running")
                is GenerationState.Finished -> {
                    add("generation=finished")
                    generation.report.items.forEach { item -> item.templateIds.forEach { add("result=${it.template}:${item.result}") } }
                }
            }
        }.joinToString("\n")
    }

    fun describeDialog(): String {
        check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
        val dialog = GenerateDialogs.showing ?: return "dialog=none"
        val state = dialog.viewModel.state.value
        return buildList {
            add("dialog=open")
            add("title=${dialog.title}")
            add("template=${state.selected?.template?.template}")
            state.selectedDefinition?.let { id -> add("definition=${state.definitions.firstOrNull { it.id == id }?.gradlePath}") }
            state.fields.forEach { add("field=${it.name}=${it.value.orEmpty()}") }
            add("target=${state.targetPath}")
            add("canGenerate=${state.canGenerate}")
            add("okEnabled=${dialog.isOKActionEnabled}")
            dialog.refusal?.let { add("refusal=$it") }
        }.joinToString("\n")
    }

    fun describeNoRole(): String {
        check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
        var description = "noRole=none"
        // Any modality: a modal dialog (Settings) must not hide what is on screen.
        ApplicationManager.getApplication().invokeAndWait({ description = describeNoRoleOnEdt() }, ModalityState.any())
        return description
    }

    private fun describeNoRoleOnEdt(): String {
        val editor = FileEditorManager.getInstance(project).selectedEditors.firstOrNull() ?: return "file=none\nnoRole=none"
        val file = editor.file?.name.orEmpty()
        val panel = NoRolePanels.of(editor) ?: return "file=$file\nnoRole=none"
        return buildList {
            add("file=$file")
            add("noRole=shown")
            add("text=${panel.lines.first}")
            panel.lines.second?.let { add("nearby=$it") }
            panel.lines.tooltip?.let { add("tooltip=$it") }
            UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java).forEach { add("link=${it.text}") }
        }.joinToString("\n")
    }

    fun checkAndFill(template: String, args: String): Boolean {
        val row = viewModel.state.value.rows.firstOrNull { it.template.template == template } ?: return false
        val inputs = args.lines().filter { it.isNotBlank() }.map { line -> line.substringBefore('=') to line.substringAfter('=') }
        onEdt {
            viewModel.dispatch(KatachiIntent.ToggleCheck(row.id))
            inputs.forEach { (name, value) -> viewModel.dispatch(KatachiIntent.Input(FieldId(row.id, name), value)) }
        }
        return true
    }

    fun describeGutter(path: String): String = gutterReader().describeGutter(path)

    fun describeHighlights(path: String): String = gutterReader().describeHighlights(path)

    fun clickGutter(path: String, line: Int): String = gutterReader().clickGutter(path, line)

    fun chooseFromGutter(path: String, line: Int, row: String): String = gutterReader().chooseFromGutter(path, line, row)

    fun describeBanners(path: String): String = gutterReader().describeBanners(path)

    fun applyFix(path: String, line: Int, prefix: String): String = gutterReader().applyFix(path, line, prefix)

    fun describeCaret(): String = gutterReader().describeCaret()

    fun chooseFromGutterLater(path: String, line: Int, row: String): String = gutterReader().chooseFromGutterLater(path, line, row)

    fun fillDialog(name: String, value: String): String = checked(DebugDialogs()).fillDialog(name, value)

    fun pressDialog(button: String): String = checked(DebugDialogs()).pressDialog(button)

    fun setGutterIcons(enabled: Boolean): String = gutterReader().setGutterIcons(enabled)

    fun describeRedraw(): String {
        checked(Unit)
        val refresher = HighlightingRefresher.getInstance(project)
        val last = refresher.lastRedraw.value
        val redraw = last?.let { "${it.seq}|${it.reason.replace("\n", "\\n")}|${it.definition?.let { state -> state::class.simpleName }}" } ?: "none"
        return "redraw=$redraw\nbusy=${refresher.isBusy}"
    }

    fun screenshotFrame(out: String): String = DebugScreenshots(project).ofFrame(checked(out))

    fun screenshotGutterMenu(path: String, line: Int, out: String): String = gutterReader().screenshotGutterMenu(path, line, out)

    fun pressNoRoleLink(text: String): String = DebugPanels(project).pressNoRoleLink(checked(text))

    fun pressEntryLinkLater(text: String): String = checked(DebugPanels(project)).pressEntryLinkLater(text)

    fun dismissNoRole(): String = checked(DebugPanels(project)).dismissNoRole()

    fun screenshotDialog(out: String): String = DebugScreenshots(project).ofDialog(checked(out))

    fun screenshotPopup(out: String): String = DebugPanels(project).screenshotPopup(checked(out))

    fun describePopup(): String = checked(DebugPanels(project)).describePopup()

    fun refreshFromDisk(): String = checked(DebugPanels(project)).refreshFromDisk()

    fun closePopups(): String = checked(DebugPanels(project)).closePopups()

    fun describeDefinitionErrors(path: String): String = authoringReader().describeDefinitionErrors(path)

    fun describeCompletion(path: String, offset: Int): String = authoringReader().describeCompletion(path, offset)

    fun describeDefinitionGutter(path: String): String = authoringReader().describeDefinitionGutter(path)

    fun describeNewMenu(directory: String): String = checked(DebugNewMenu(project)).describeNewMenu(directory)

    fun chooseNewMenuItemLater(directory: String, path: String): String = checked(DebugNewMenu(project)).chooseNewMenuItemLater(directory, path)

    private fun authoringReader(): AuthoringDebugReader = AuthoringDebugReader(project, onScreen).also { checked(it) }

    private fun <T> checked(value: T): T {
        check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
        return value
    }

    private fun gutterReader(): GutterDebugReader {
        check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
        return GutterDebugReader(project, onScreen)
    }

    fun generate() = onEdt { viewModel.dispatch(KatachiIntent.Generate) }

    fun uncheckAll() = onEdt { viewModel.dispatch(KatachiIntent.UncheckAll) }

    private fun onEdt(block: () -> Unit) = ApplicationManager.getApplication().invokeAndWait(block)
}

private const val ENABLED_PROPERTY = "katachi.debugBridge"

private const val PHASE_TEXT_LIMIT = 2_000
