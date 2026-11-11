package me.tbsten.katachi.intellij.data.detect

import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId

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
    var taskListMissing = false
    for (root in roots) {
        for (module in root.modules.sortedBy { it.gradlePath }) {
            if (module.taskNames.isEmpty()) taskListMissing = true
            val capabilities = capabilitiesOf(module.taskNames)
            val katachiModule = KatachiModule(module.gradlePath, module.directory, root.rootPath, root.rootName, capabilities)
            module.katachiVersion?.let { versions[katachiModule.id] = it }
            when {
                capabilities.isNotEmpty() -> found += katachiModule
                KatachiModule.DESCRIBE_TEMPLATES_TASK in module.taskNames -> {
                    hasOutdated = true
                    outdatedVersion = outdatedVersion ?: module.katachiVersion
                }
                // Which task it has is not known: loading tries the definition task first (design 4.1).
                // Judged per module, not per root: Android Studio leaves the task lists out, but another
                // module of the same root may still come with one (KATACHI-363).
                module.taskNames.isEmpty() && module.katachiVersion != null -> candidates += katachiModule
            }
        }
    }
    return when {
        found.isNotEmpty() -> DetectionResult.Found(found, versions.filterKeys { id -> found.any { it.id == id } })
        candidates.isNotEmpty() -> DetectionResult.TaskListMissing(candidates)
        hasOutdated -> DetectionResult.Outdated(outdatedVersion)
        else -> DetectionResult.NotInstalled(taskListMissing)
    }
}

internal fun capabilitiesOf(taskNames: Set<String>): Set<KatachiCapability> = buildSet {
    if (KatachiModule.DEFINITION_JSON_TASK in taskNames) add(KatachiCapability.Definition)
    if (KatachiModule.TEMPLATES_JSON_TASK in taskNames) add(KatachiCapability.Templates)
}
