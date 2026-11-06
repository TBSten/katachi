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

internal class KatachiSettingsState : BaseState() {
    var autoReloadOnSave by property(true)
    var openAfterGeneration by enum(OpenAfterGeneration.First)

    var onExisting by enum(OnExistingChoice.Fail)

    var editorNotificationEnabled by property(true)

    var notifyOnEmptyFile by property(true)

    var notifyOnFileWithContent by property(true)

    var notifyOnContentOnce by property(true)

    var notifyOnNoRole by property(true)

    var autoLoadTemplates by property(true)

    var showViolationsInEditor by property(true)

    // Off until the user turns it on: an AI agent then writes files without anybody pressing Generate.
    var mcpAllowGenerate by property(false)

    var collapsedModules by stringSet()
}

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

    var notifyOnNoRole: Boolean
        get() = state.notifyOnNoRole
        set(value) {
            state.notifyOnNoRole = value
        }

    var autoLoadTemplates: Boolean
        get() = state.autoLoadTemplates
        set(value) {
            state.autoLoadTemplates = value
        }

    var showViolationsInEditor: Boolean
        get() = state.showViolationsInEditor
        set(value) {
            state.showViolationsInEditor = value
        }

    var mcpAllowGenerate: Boolean
        get() = state.mcpAllowGenerate
        set(value) {
            state.mcpAllowGenerate = value
        }

    var openAfterGeneration: OpenAfterGeneration
        get() = state.openAfterGeneration
        set(value) {
            state.openAfterGeneration = value
        }

    // "Overwrite" is never remembered: picked on another day it would replace files without asking.
    var onExisting: OnExistingChoice
        get() = state.onExisting.takeUnless { it == OnExistingChoice.Overwrite } ?: OnExistingChoice.Fail
        set(value) {
            if (value != OnExistingChoice.Overwrite) state.onExisting = value
        }

    var collapsedModules: Set<ModuleId>
        get() = state.collapsedModules.mapNotNull(::moduleIdOf).toSet()
        set(value) {
            // Assigned whole rather than changed in place: the state's setter copies the elements in
            // and counts the modification, which clear() / addAll() on the set would not.
            state.collapsedModules = value.mapTo(mutableSetOf()) { "${it.linkedRootPath}$SEPARATOR${it.gradlePath}" }
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
