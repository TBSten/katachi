package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.KatachiConfigurable
import me.tbsten.katachi.intellij.ide.RefreshDefinitionAction
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
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

/** The IDE's [RuleEffects]. A call that fails is logged by `sdkCall`; TODO: tell the user in a balloon, as the entries do. */
internal class RuleEffectsImpl(private val project: Project) : RuleEffects {
    override fun open(site: ResolvedSite.Found) {
        // The site's line is 1-based, the descriptor's 0-based.
        sdkCall("open ${site.file.path}") { OpenFileDescriptor(project, site.file, (site.line - 1).coerceAtLeast(0), 0).navigate(true) }
    }

    override fun copy(text: String) {
        sdkCall("copy the role declaration draft") { CopyPasteManager.getInstance().setContents(StringSelection(text)) }
    }

    override fun refreshDefinition() = RefreshDefinitionAction.refresh(project)

    override fun openSettings() {
        sdkCall("open the katachi settings") { ShowSettingsUtil.getInstance().showSettingsDialog(project, KatachiConfigurable::class.java) }
    }
}
