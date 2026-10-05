package me.tbsten.katachi.intellij.data.detect

import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId

/**
 * Finds the definition modules in the synced data, without running Gradle: a module is one when it
 * has `katachiInternalDefinitionJson` or `katachiInternalTemplatesJson`, whatever its name (spec 01,
 * design 4.1). Which of the two it has goes to [KatachiModule.capabilities].
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

/** The internal tasks among [taskNames]. Empty: not a definition module (or a sync without a task list). */
internal fun capabilitiesOf(taskNames: Set<String>): Set<KatachiCapability> = buildSet {
    if (KatachiModule.DEFINITION_JSON_TASK in taskNames) add(KatachiCapability.Definition)
    if (KatachiModule.TEMPLATES_JSON_TASK in taskNames) add(KatachiCapability.Templates)
}
