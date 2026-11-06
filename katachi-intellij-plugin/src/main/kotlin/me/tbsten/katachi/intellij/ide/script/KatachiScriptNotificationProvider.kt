package me.tbsten.katachi.intellij.ide.script

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import me.tbsten.katachi.intellij.data.script.ScriptClasspath
import me.tbsten.katachi.intellij.data.script.isKatachiScript
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.sdkCall
import java.awt.datatransfer.StringSelection
import java.util.function.Function
import javax.swing.JComponent

/**
 * The banner of a `*.katachi.kts` whose definition could not be had from the katachi CLI: none installed, one from
 * before `*.katachi.kts`, or one whose answer could not be read. It says how to get one, and checks again on request.
 * Nothing is shown while the CLI has not answered yet, nor on any other file.
 */
internal class KatachiScriptNotificationProvider(
    private val answerOf: () -> ScriptClasspath?,
    private val recheck: () -> Unit,
    private val copy: (String) -> Unit = { text -> CopyPasteManager.getInstance().setContents(StringSelection(text)) },
) : EditorNotificationProvider, DumbAware {
    @Suppress("unused") // Called by the platform.
    constructor() : this({ KatachiScriptService.getInstance().current() }, { KatachiScriptService.getInstance().recheck() })

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!isKatachiScript(file.name)) return null
        val banner = sdkCall("decide the katachi script notification of ${file.name}") { answerOf()?.let(::bannerOf) }.getOrNull() ?: return null
        return Function { editor -> sdkCall("build the katachi script notification of ${file.name}") { panelOf(editor, banner) }.getOrNull() }
    }

    private fun panelOf(editor: FileEditor, banner: Banner): EditorNotificationPanel {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning)
        panel.text = banner.text
        panel.createActionLabel(KatachiBundle.message(banner.copyKey, banner.command)) {
            sdkCall("copy ${banner.command}") { copy(banner.command) }
        }
        panel.createActionLabel(KatachiBundle.message("script.notification.recheck")) {
            sdkCall("ask the katachi CLI again") { recheck() }
        }
        return panel
    }

    internal class Banner(val text: String, val copyKey: String, val command: String)

    internal companion object {
        const val INSTALL_COMMAND: String = "brew install tbsten/tap/katachi"
        const val UPGRADE_COMMAND: String = "brew upgrade tbsten/tap/katachi"

        fun bannerOf(answer: ScriptClasspath): Banner? = when (answer) {
            is ScriptClasspath.Found -> null
            ScriptClasspath.CliNotFound ->
                Banner(KatachiBundle.message("script.notification.cliNotFound"), "script.notification.copy", INSTALL_COMMAND)
            is ScriptClasspath.Unsupported ->
                Banner(KatachiBundle.message("script.notification.unsupported"), "script.notification.copy", UPGRADE_COMMAND)
            is ScriptClasspath.Malformed ->
                Banner(KatachiBundle.message("script.notification.malformed", answer.detail), "script.notification.copy", UPGRADE_COMMAND)
        }
    }
}
