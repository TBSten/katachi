package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import java.nio.file.Path

/** What [KatachiSettings] writes to the workspace file. */
internal class KatachiSettingsState : BaseState() {
    /** "Reload when a definition is saved" (E-44). Off by default: a definition does not compile while being edited. */
    var autoReloadOnSave by property(false)
    var openAfterGeneration by enum(OpenAfterGeneration.First)

    /** The "existing files" combo, remembered per project (spec 04 "入力値の保存"); never "overwrite". */
    var onExisting by enum(OnExistingChoice.Fail)

    /** Show notifications in the editor at all (the master switch of the three below). */
    var editorNotificationEnabled by property(true)

    /** Notify on an empty file that a template could make. */
    var notifyOnEmptyFile by property(true)

    /** Notify on a file that has content. */
    var notifyOnFileWithContent by property(true)

    /** Notify on a file with content only the first time it is opened (the memory of that is not persisted). */
    var notifyOnContentOnce by property(true)

    /** Load the templates without a user action, which runs Gradle. */
    var autoLoadTemplates by property(true)

    /** Folded module header bands, as `<linked root>|<Gradle path>`. */
    var collapsedModules by stringSet()
}

/**
 * The per-project katachi settings, kept in the workspace file because they are one person's
 * preferences. [KatachiConfigurable] edits all but the last three; the tool window writes the rest.
 * What the user dismissed with the notification's close button, and which files already got their
 * "first time" notification, are deliberately not kept here: they last for the IDE session only.
 *
 * ```kotlin
 * val open = KatachiSettings.getInstance(project).openAfterGeneration
 * ```
 */
@Service(Service.Level.PROJECT)
@State(name = "KatachiSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class KatachiSettings : SimplePersistentStateComponent<KatachiSettingsState>(KatachiSettingsState()) {
    var autoReloadOnSave: Boolean
        get() = state.autoReloadOnSave
        set(value) {
            state.autoReloadOnSave = value
        }

    var editorNotificationEnabled: Boolean
        get() = state.editorNotificationEnabled
        set(value) {
            state.editorNotificationEnabled = value
        }

    var notifyOnEmptyFile: Boolean
        get() = state.notifyOnEmptyFile
        set(value) {
            state.notifyOnEmptyFile = value
        }

    var notifyOnFileWithContent: Boolean
        get() = state.notifyOnFileWithContent
        set(value) {
            state.notifyOnFileWithContent = value
        }

    var notifyOnContentOnce: Boolean
        get() = state.notifyOnContentOnce
        set(value) {
            state.notifyOnContentOnce = value
        }

    var autoLoadTemplates: Boolean
        get() = state.autoLoadTemplates
        set(value) {
            state.autoLoadTemplates = value
        }

    var openAfterGeneration: OpenAfterGeneration
        get() = state.openAfterGeneration
        set(value) {
            state.openAfterGeneration = value
        }

    /**
     * The "existing files" combo. "Overwrite" is not remembered: picked on another day it would
     * replace files without asking, so the project keeps the choice from before it (provisional,
     * spec 04 remembers every choice).
     */
    var onExisting: OnExistingChoice
        get() = state.onExisting.takeUnless { it == OnExistingChoice.Overwrite } ?: OnExistingChoice.Fail
        set(value) {
            if (value != OnExistingChoice.Overwrite) state.onExisting = value
        }

    var collapsedModules: Set<ModuleId>
        get() = state.collapsedModules.mapNotNull(::moduleIdOf).toSet()
        set(value) {
            state.collapsedModules.clear()
            state.collapsedModules.addAll(value.map { "${it.linkedRootPath}$SEPARATOR${it.gradlePath}" })
            state.intIncrementModificationCount()
        }

    companion object {
        private const val SEPARATOR = "|"

        fun getInstance(project: Project): KatachiSettings = project.service()

        private fun moduleIdOf(text: String): ModuleId? {
            val separator = text.lastIndexOf(SEPARATOR)
            if (separator <= 0) return null
            return ModuleId(Path.of(text.substring(0, separator)), text.substring(separator + 1))
        }
    }
}
