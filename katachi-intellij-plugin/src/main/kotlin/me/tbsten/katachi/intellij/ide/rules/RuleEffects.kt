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

/**
 * What the clicks of the "no role holds this file" notification and its popup do in the IDE. Each is called on the
 * EDT; a test records them instead.
 *
 * ```kotlin
 * effects.open(site)          // a nearby role's declaration
 * effects.copy(draft)         // "Copy a role declaration draft"
 * ```
 */
internal interface RuleEffects {
    /** Opens [site]'s file at its line. */
    fun open(site: ResolvedSite.Found)

    /** Puts [text] on the clipboard. */
    fun copy(text: String)

    /** Reads the definition again (`katachi.RefreshDefinition`). */
    fun refreshDefinition()

    fun openSettings()
}

/**
 * The IDE's [RuleEffects]. A call that fails is logged by `sdkCall` and told to the user in a balloon
 * (`katachi: Could not open the definition: ...`), as the entries do.
 */
internal class RuleEffectsImpl(
    private val project: Project,
    /** Opens a file at a 0-based line; a test makes it throw to play an SDK call that fails. */
    private val navigate: (VirtualFile, Int) -> Unit = { file, line -> OpenFileDescriptor(project, file, line, 0).navigate(true) },
    /** Puts text on the clipboard; replaced by a test in the same way. */
    private val setClipboard: (String) -> Unit = { CopyPasteManager.getInstance().setContents(StringSelection(it)) },
    /** Shows the katachi settings; replaced by a test in the same way. */
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
