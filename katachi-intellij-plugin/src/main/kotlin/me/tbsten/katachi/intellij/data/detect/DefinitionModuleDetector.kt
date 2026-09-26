package me.tbsten.katachi.intellij.data.detect

import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId

/**
 * Finds the definition modules in the synced data, without running Gradle: a module is one when it
 * has `katachiInternalTemplatesJson`, whatever its name (spec 01).
 *
 * Roots come in path order and modules in Gradle path order within a root (E-05, E-32). A root whose
 * modules carry no task at all is treated as a sync without a task list (E-30), and its modules that
 * depend on katachi become candidates.
 */
internal fun detectDefinitionModules(project: SyncedProject): DetectionResult {
    val roots = when (project) {
        SyncedProject.NotGradle -> return DetectionResult.NotGradle
        SyncedProject.NotSynced -> return DetectionResult.NotSynced
        is SyncedProject.Synced -> project.roots.sortedBy { it.rootPath.toString() }
    }
    if (roots.isEmpty()) return DetectionResult.NotSynced

    val found = mutableListOf<KatachiModule>()
    val candidates = mutableListOf<KatachiModule>()
    val versions = LinkedHashMap<ModuleId, String>()
    var outdatedVersion: String? = null
    var hasOutdated = false
    for (root in roots) {
        val taskListMissing = root.modules.isNotEmpty() && root.modules.all { it.taskNames.isEmpty() }
        for (module in root.modules.sortedBy { it.gradlePath }) {
            val katachiModule = KatachiModule(module.gradlePath, module.directory, root.rootPath, root.rootName)
            module.katachiVersion?.let { versions[katachiModule.id] = it }
            when {
                KatachiModule.TEMPLATES_JSON_TASK in module.taskNames -> found += katachiModule
                KatachiModule.DESCRIBE_TEMPLATES_TASK in module.taskNames -> {
                    hasOutdated = true
                    outdatedVersion = outdatedVersion ?: module.katachiVersion
                }
                taskListMissing && module.katachiVersion != null -> candidates += katachiModule
            }
        }
    }
    return when {
        found.isNotEmpty() -> DetectionResult.Found(found, versions.filterKeys { id -> found.any { it.id == id } })
        candidates.isNotEmpty() -> DetectionResult.TaskListMissing(candidates)
        hasOutdated -> DetectionResult.Outdated(outdatedVersion)
        else -> DetectionResult.NotInstalled
    }
}
