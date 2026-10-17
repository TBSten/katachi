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

/**
 * A String-only door into the tool window's ViewModel for the Driver smoke (`integrationTest`).
 *
 * The screen is one `ComposePanel`, whose rows and fields Driver's Swing tree cannot see, so the
 * smoke opens the tool window through the IDE and then drives the same intents a click would send
 * through this service (Driver `@Remote` calls can only pass plain values). Not used by the plugin,
 * and refuses to act unless the IDE runs with `-Dkatachi.debugBridge=true`, which only the smoke sets.
 *
 * Example, from a Driver test:
 * ```kotlin
 * val bridge = driver.service(KatachiDebugBridgeRef::class, project)
 * bridge.checkAndFill("domain.Service", "name=Probe")
 * bridge.generate()
 * ```
 */
@Service(Service.Level.PROJECT)
internal class KatachiDebugBridge(private val project: Project) {
    /** Whether a part is on screen; a headless test, which has no screen, replaces it. */
    @TestOnly
    @Volatile
    internal var onScreen: (Component) -> Boolean = { it.isShowing }


    private val viewModel: KatachiToolWindowViewModel
        get() {
            check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
            return KatachiProjectService.getInstance(project).viewModel
        }

    /**
     * The state as `key=value` lines: `phase`, one `module` per definition module (its Gradle path),
     * one `template` per row (its complete specifier), `blocker` and
     * `generation` (`running`, or `finished` followed by one `result` line per template).
     */
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

    /**
     * The generate dialog on screen as `key=value` lines, or `dialog=none`: `dialog=open`, `title`, `template`
     * (its complete specifier), `definition`, one `field` per input (`name=value`), `target`, `canGenerate`,
     * `okEnabled` and `refusal`. Reads state only, so it works while the modal dialog blocks the EDT.
     */
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

    /**
     * The "no role holds this file" notification on the selected editor as `key=value` lines: `file`, then `noRole=none`,
     * or `noRole=shown`, `text` (the first line), `nearby` (the second, when there is one), `tooltip` (when there is one)
     * and one `link` per link in the order on screen. Reads what is on screen and nothing else, so it works while a popup
     * is open.
     */
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

    /**
     * Checks the first row whose complete specifier is [template] and types [args] into its
     * fields, one `name=value` per line. `false` when no row has that specifier.
     */
    fun checkAndFill(template: String, args: String): Boolean {
        val row = viewModel.state.value.rows.firstOrNull { it.template.template == template } ?: return false
        val inputs = args.lines().filter { it.isNotBlank() }.map { line -> line.substringBefore('=') to line.substringAfter('=') }
        onEdt {
            viewModel.dispatch(KatachiIntent.ToggleCheck(row.id))
            inputs.forEach { (name, value) -> viewModel.dispatch(KatachiIntent.Input(FieldId(row.id, name), value)) }
        }
        return true
    }

    /**
     * The katachi ◆ of [path], a file open in the editor, as the daemon drew them: `gutter`, `open`, `marks` and one
     * `mark=<line>|menu|<leaf>|<tooltip>` each (see [GutterDebugReader.describeGutter]).
     */
    fun describeGutter(path: String): String = gutterReader().describeGutter(path)

    /** The katachi annotations of [path] as the daemon holds them: severity, range, description and fixes (see [GutterDebugReader.describeHighlights]). */
    fun describeHighlights(path: String): String = gutterReader().describeHighlights(path)

    /** Opens the menu of the ◆ on [line] of [path], returns its rows and closes it (see [GutterDebugReader.clickGutter]). */
    fun clickGutter(path: String, line: Int): String = gutterReader().clickGutter(path, line)

    /** Chooses the row starting with [row] in the menu of the ◆ on [line] of [path]: `chosen=<row>` or `chosen=none`. */
    fun chooseFromGutter(path: String, line: Int, row: String): String = gutterReader().chooseFromGutter(path, line, row)

    /** The katachi banners on [path], read from the parts on screen (see [GutterDebugReader.describeBanners]). */
    fun describeBanners(path: String): String = gutterReader().describeBanners(path)

    /** Applies the fix starting with [prefix] of the annotation on [line] of [path]: `applied=<text>` or `applied=none` (see [GutterDebugReader.applyFix]). */
    fun applyFix(path: String, line: Int, prefix: String): String = gutterReader().applyFix(path, line, prefix)

    /** The selected text editor's file and caret line (see [GutterDebugReader.describeCaret]). */
    fun describeCaret(): String = gutterReader().describeCaret()

    /** [chooseFromGutter] that leaves the choosing for later on the EDT, for a row that opens a modal dialog (see [GutterDebugReader.chooseFromGutterLater]). */
    fun chooseFromGutterLater(path: String, line: Int, row: String): String = gutterReader().chooseFromGutterLater(path, line, row)

    /** Types [value] into the field [name] of the generate dialog on screen: `filled=<name>` or `filled=none` (see [DebugDialogs.fillDialog]). */
    fun fillDialog(name: String, value: String): String = checked(DebugDialogs()).fillDialog(name, value)

    /** Presses `generate` or `cancel` of the generate dialog on screen: `pressed=<button>` or `pressed=none` (see [DebugDialogs.pressDialog]). */
    fun pressDialog(button: String): String = checked(DebugDialogs()).pressDialog(button)

    /** Turns the katachi gutter icons on or off and draws again: `gutterIcons=<true or false>` (see [GutterDebugReader.setGutterIcons]). */
    fun setGutterIcons(enabled: Boolean): String = gutterReader().setGutterIcons(enabled)

    /** The last draw of the gutter: `redraw=<seq>|<reason>|<state type>` or `redraw=none`, and `busy=<bool>`. A failing smoke adds it to its message. */
    fun describeRedraw(): String {
        checked(Unit)
        val refresher = HighlightingRefresher.getInstance(project)
        val last = refresher.lastRedraw.value
        val redraw = last?.let { "${it.seq}|${it.reason.replace("\n", "\\n")}|${it.definition?.let { state -> state::class.simpleName }}" } ?: "none"
        return "redraw=$redraw\nbusy=${refresher.isBusy}"
    }

    /** Writes the IDE window as a PNG to [out]: `saved=<out>|<w>x<h>` (see [DebugScreenshots.ofFrame]). */
    fun screenshotFrame(out: String): String = DebugScreenshots(project).ofFrame(checked(out))

    /** Writes the menu of the ◆ on [line] of [path] as a PNG to [out] (see [GutterDebugReader.screenshotGutterMenu]). */
    fun screenshotGutterMenu(path: String, line: Int, out: String): String = gutterReader().screenshotGutterMenu(path, line, out)

    /** Presses the link [text] of the no-role notification: `pressed=<text>` or `pressed=none` (see [DebugPanels.pressNoRoleLink]). */
    fun pressNoRoleLink(text: String): String = DebugPanels(project).pressNoRoleLink(checked(text))

    /** Presses × of the no-role notification (see [DebugPanels.dismissNoRole]). */
    fun dismissNoRole(): String = checked(DebugPanels(project)).dismissNoRole()

    /** Writes the popup on screen as a PNG to [out] (see [DebugPanels.screenshotPopup]). */
    fun screenshotPopup(out: String): String = DebugPanels(project).screenshotPopup(checked(out))

    /** The rows of the popup on screen (see [DebugPanels.describePopup]). */
    fun describePopup(): String = checked(DebugPanels(project)).describePopup()

    /** Looks at the disk again, as coming back to the IDE does (see [DebugPanels.refreshFromDisk]). */
    fun refreshFromDisk(): String = checked(DebugPanels(project)).refreshFromDisk()

    /** Closes the popups on screen (see [DebugPanels.closePopups]). */
    fun closePopups(): String = checked(DebugPanels(project)).closePopups()

    /** The mistakes katachi found in the definition and where the IDE put them in [path] (see [AuthoringDebugReader.describeDefinitionErrors]). */
    fun describeDefinitionErrors(path: String): String = authoringReader().describeDefinitionErrors(path)

    /** What a completion at [offset] of [path] would offer (see [AuthoringDebugReader.describeCompletion]). */
    fun describeCompletion(path: String, offset: Int): String = authoringReader().describeCompletion(path, offset)

    /** The ◆, their menus and the Code Vision of the definition file [path] (see [AuthoringDebugReader.describeDefinitionGutter]). */
    fun describeDefinitionGutter(path: String): String = authoringReader().describeDefinitionGutter(path)

    private fun authoringReader(): AuthoringDebugReader = AuthoringDebugReader(project, onScreen).also { checked(it) }

    private fun <T> checked(value: T): T {
        check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
        return value
    }

    private fun gutterReader(): GutterDebugReader {
        check(System.getProperty(ENABLED_PROPERTY) == "true") { "The katachi debug bridge is off; start the IDE with -D$ENABLED_PROPERTY=true." }
        return GutterDebugReader(project, onScreen)
    }

    /** Presses Generate. */
    fun generate() = onEdt { viewModel.dispatch(KatachiIntent.Generate) }

    /** Presses "uncheck all", which also leaves the result of the last generation. */
    fun uncheckAll() = onEdt { viewModel.dispatch(KatachiIntent.UncheckAll) }

    private fun onEdt(block: () -> Unit) = ApplicationManager.getApplication().invokeAndWait(block)
}

/** The system property that turns the bridge on. */
private const val ENABLED_PROPERTY = "katachi.debugBridge"

/** A load error carries the whole Gradle output; the smoke only needs its start. */
private const val PHASE_TEXT_LIMIT = 2_000
