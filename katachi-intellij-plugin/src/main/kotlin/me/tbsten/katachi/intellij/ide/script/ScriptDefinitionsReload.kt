package me.tbsten.katachi.intellij.ide.script

import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.sdkCall
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider

/**
 * Makes the Kotlin plugin ask the definition sources again, so that `*.katachi.kts` gets its definition as soon as the
 * CLI answers, without a restart. The Kotlin plugin has no public way to do it: the K2 provider caches the definitions
 * by a modification tracker of an internal module, and the K1 one has `reloadDefinitions()`. Both are reached by
 * reflection, through the class loader of the provider itself, so that a Kotlin plugin that moved them costs only
 * this (the definition then comes with the next start), never an error.
 */
internal object ScriptDefinitionsReload {
    private const val K2_TRACKER = "org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker"

    fun reload(project: Project): Boolean = try {
        sdkCall("reload the Kotlin script definitions") { reloadOrFalse(project) }.getOrDefault(false)
    } catch (_: LinkageError) {
        false
    }

    private fun reloadOrFalse(project: Project): Boolean {
        val provider = project.getService(ScriptDefinitionProvider::class.java) ?: return false
        val tracker = try {
            provider.javaClass.classLoader.loadClass(K2_TRACKER)
        } catch (_: ClassNotFoundException) {
            null
        }
        if (tracker != null) {
            val instance = tracker.getMethod("getInstance", Project::class.java).invoke(null, project)
            tracker.getMethod("incModificationCount").invoke(instance)
            return true
        }
        provider.javaClass.getMethod("reloadDefinitions").invoke(provider)
        return true
    }
}
