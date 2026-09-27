package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration

/** Settings | Tools | katachi: the two choices [KatachiSettings] offers. */
internal class KatachiConfigurable(private val project: Project) : BoundConfigurable(KatachiBundle.message("settings.displayName")) {
    /** An empty page when the IDE fails to build it, rather than an error over the Settings dialog. */
    override fun createPanel(): DialogPanel = sdkCall("build the katachi settings page") {
        val settings = KatachiSettings.getInstance(project)
        panel {
            row {
                checkBox(KatachiBundle.message("settings.autoReload")).bindSelected(settings::autoReloadOnSave)
            }
            buttonsGroup(KatachiBundle.message("settings.openAfter")) {
                row { radioButton(KatachiBundle.message("settings.open.first"), OpenAfterGeneration.First) }
                row { radioButton(KatachiBundle.message("settings.open.all"), OpenAfterGeneration.All) }
                row { radioButton(KatachiBundle.message("settings.open.none"), OpenAfterGeneration.None) }
            }.bind(settings::openAfterGeneration)
        }
    }.getOrElse { DialogPanel() }

    companion object {
        /** The `id` of `<projectConfigurable>` in plugin.xml, for opening this page from the tool window. */
        const val ID: String = "me.tbsten.katachi.intellij.settings"
    }
}
