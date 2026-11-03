package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiConfigurable
import me.tbsten.katachi.intellij.ide.RefreshDefinitionAction
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterNavigation
import me.tbsten.katachi.intellij.ide.sdkCall
import java.awt.datatransfer.StringSelection

// Each is called on the EDT.
internal interface RuleEffects {
    fun open(site: ResolvedSite.Found)

    fun copy(text: String)

    fun refreshDefinition()

    fun openSettings()
}

internal class RuleEffectsImpl(
    private val project: Project,
    private val navigate: (VirtualFile, Int) -> Unit = { file, line -> OpenFileDescriptor(project, file, line, 0).navigate(true) },
    private val setClipboard: (String) -> Unit = { CopyPasteManager.getInstance().setContents(StringSelection(it)) },
    private val showSettings: () -> Unit = { ShowSettingsUtil.getInstance().showSettingsDialog(project, KatachiConfigurable::class.java) },
) : RuleEffects {
    override fun open(site: ResolvedSite.Found) {
        // The site's line is 1-based, the descriptor's 0-based.
        sdkCall("open ${site.file.path}") { navigate(site.file, (site.line - 1).coerceAtLeast(0)) }
            .onFailure { failed("rules.failed.open", it) }
    }

    override fun copy(text: String) {
        sdkCall("copy the role declaration draft") { setClipboard(text) }
            .onFailure { failed("rules.failed.copy", it) }
    }

    override fun refreshDefinition() = RefreshDefinitionAction.refresh(project)

    override fun openSettings() {
        sdkCall("open the katachi settings") { showSettings() }
            .onFailure { failed("rules.failed.settings", it) }
    }

    private fun failed(operationKey: String, cause: Throwable) {
        GutterNavigation.balloon(project, KatachiBundle.message("rules.failed", KatachiBundle.message(operationKey), cause.message.orEmpty()))
    }
}
