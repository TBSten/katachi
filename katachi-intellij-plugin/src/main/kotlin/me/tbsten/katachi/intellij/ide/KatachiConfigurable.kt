package me.tbsten.katachi.intellij.ide

import com.intellij.ide.DataManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
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
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import java.awt.Component

/**
 * Settings | Tools | katachi: the choices [KatachiSettings] offers, in three groups (definition, editor,
 * generation). The four detail notification
 * rows (the three about templates and "files no role allows") are disabled while the master switch
 * is off, and "only the first time" while the "files with content" row is off.
 */
/** Opens one of the IDE's own Settings pages by its configurable id. */
internal fun interface SettingsPageOpener {
    /** [source] is the component that was clicked, to find the Settings dialog it sits in; null when unknown. */
    fun open(project: Project, source: Component?, pageId: String)
}

/** Selects the page in the Settings dialog already open around [source]; opens a dialog only when there is none. */
internal object DefaultSettingsPageOpener : SettingsPageOpener {
    override fun open(project: Project, source: Component?, pageId: String) {
        val settings: Settings? = source?.let { Settings.KEY.getData(DataManager.getInstance().getDataContext(it)) }
        val target = settings?.find(pageId)
        if (settings != null && target != null) {
            settings.select(target)
        } else {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                { it is SearchableConfigurable && it.id == pageId },
                null,
            )
        }
    }
}

internal class KatachiConfigurable(private val project: Project) : BoundConfigurable(KatachiBundle.message("settings.displayName")) {
    /** Replaced by tests, which have no Settings dialog to select a page in. */
    internal var pageOpener: SettingsPageOpener = DefaultSettingsPageOpener

    /** An empty page when the IDE fails to build it, rather than an error over the Settings dialog. */
    override fun createPanel(): DialogPanel = sdkCall("build the katachi settings page") {
        val settings = KatachiSettings.getInstance(project)
        panel {
            group(KatachiBundle.message("settings.group.definition")) {
                row {
                    checkBox(KatachiBundle.message("settings.editorNotification.autoLoad"))
                        .bindSelected(settings::autoLoadTemplates)
                }
                row {
                    checkBox(KatachiBundle.message("settings.autoReload"))
                        .bindSelected(settings::autoReloadOnSave)
                        .comment(KatachiBundle.message("settings.autoReload.comment"))
                }
            }
            group(KatachiBundle.message("settings.group.editor")) {
                row {
                    checkBox(KatachiBundle.message("settings.showViolations")).bindSelected(settings::showViolationsInEditor)
                }
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
                    row {
                        checkBox(KatachiBundle.message("settings.rules.noRole"))
                            .bindSelected(settings::notifyOnNoRole)
                            .enabledIf(enabled.selected)
                    }
                }
                row {
                    // The link opens the IDE's own page; when it cannot, the sentence still tells where to go.
                    comment(KatachiBundle.message("settings.gutterIcons.comment")) { event ->
                        sdkCall("open the Gutter Icons settings") {
                            pageOpener.open(project, event.source as? Component, GUTTER_ICONS_ID)
                        }
                    }
                }
            }
            group(KatachiBundle.message("settings.group.generation")) {
                buttonsGroup(KatachiBundle.message("settings.openAfter")) {
                    row { radioButton(KatachiBundle.message("settings.open.first"), OpenAfterGeneration.First) }
                    row { radioButton(KatachiBundle.message("settings.open.all"), OpenAfterGeneration.All) }
                    row { radioButton(KatachiBundle.message("settings.open.none"), OpenAfterGeneration.None) }
                }.bind(settings::openAfterGeneration)
            }
        }
    }.getOrElse { DialogPanel() }

    /** Applies the page, then asks the open editors to redraw their notifications and annotations for the new choices. */
    override fun apply() {
        val showViolations = KatachiSettings.getInstance(project).showViolationsInEditor
        super.apply()
        sdkCall("update the editor notifications") { EditorNotifications.getInstance(project).updateAllNotifications() }
        // The annotator is asked only when the daemon runs again: an unchanged file would keep (or lack) the underlines.
        if (showViolations != KatachiSettings.getInstance(project).showViolationsInEditor) {
            HighlightingRefresher.getInstance(project).redrawNow("the setting of the violation annotations changed")
        }
    }

    companion object {
        /** The `id` of `<projectConfigurable>` in plugin.xml, for opening this page from the tool window. */
        const val ID: String = "me.tbsten.katachi.intellij.settings"

        /** The configurable id of the IDE's page that lists the gutter icons, where the ◆ is switched (not its translated name). */
        internal const val GUTTER_ICONS_ID: String = "editor.preferences.gutterIcons"
    }
}
