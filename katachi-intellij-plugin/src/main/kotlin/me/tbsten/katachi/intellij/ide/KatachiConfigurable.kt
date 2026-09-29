package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.and
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration

/**
 * Settings | Tools | katachi: the choices [KatachiSettings] offers. The three detail notification
 * rows are disabled while the master switch is off, and "only the first time" while the
 * "files with content" row is off.
 */
internal class KatachiConfigurable(private val project: Project) : BoundConfigurable(KatachiBundle.message("settings.displayName")) {
    /** An empty page when the IDE fails to build it, rather than an error over the Settings dialog. */
    override fun createPanel(): DialogPanel = sdkCall("build the katachi settings page") {
        val settings = KatachiSettings.getInstance(project)
        panel {
            row {
                checkBox(KatachiBundle.message("settings.autoReload")).bindSelected(settings::autoReloadOnSave)
            }
            run {
                lateinit var enabled: Cell<JBCheckBox>
                lateinit var withContent: Cell<JBCheckBox>
                row {
                    enabled = checkBox(KatachiBundle.message("settings.editorNotification.enabled"))
                        .bindSelected(settings::editorNotificationEnabled)
                }
                indent {
                    row {
                        checkBox(KatachiBundle.message("settings.editorNotification.empty"))
                            .bindSelected(settings::notifyOnEmptyFile)
                            .enabledIf(enabled.selected)
                    }
                    row {
                        withContent = checkBox(KatachiBundle.message("settings.editorNotification.content"))
                            .bindSelected(settings::notifyOnFileWithContent)
                            .enabledIf(enabled.selected)
                    }
                    indent {
                        row {
                            checkBox(KatachiBundle.message("settings.editorNotification.contentOnce"))
                                .bindSelected(settings::notifyOnContentOnce)
                                .enabledIf(enabled.selected and withContent.selected)
                        }
                    }
                }
                row {
                    checkBox(KatachiBundle.message("settings.editorNotification.autoLoad"))
                        .bindSelected(settings::autoLoadTemplates)
                }
            }
            buttonsGroup(KatachiBundle.message("settings.openAfter")) {
                row { radioButton(KatachiBundle.message("settings.open.first"), OpenAfterGeneration.First) }
                row { radioButton(KatachiBundle.message("settings.open.all"), OpenAfterGeneration.All) }
                row { radioButton(KatachiBundle.message("settings.open.none"), OpenAfterGeneration.None) }
            }.bind(settings::openAfterGeneration)
        }
    }.getOrElse { DialogPanel() }

    /** Applies the page, then asks the open editors to redraw their notifications for the new choices. */
    override fun apply() {
        super.apply()
        sdkCall("update the editor notifications") { EditorNotifications.getInstance(project).updateAllNotifications() }
    }

    companion object {
        /** The `id` of `<projectConfigurable>` in plugin.xml, for opening this page from the tool window. */
        const val ID: String = "me.tbsten.katachi.intellij.settings"
    }
}
