package me.tbsten.katachi.intellij.data.watch

import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import java.nio.file.Path

internal data class WatchedModule(
    val module: KatachiModule,
    val testRoots: List<Path>,
)

internal data class FileChanges(
    val definitionChanged: Set<ModuleId> = emptySet(),
    val testSourcesChanged: Set<ModuleId> = emptySet(),
    val sarifChanged: Set<ModuleId> = emptySet(),
    val projectChanged: Boolean = false,
    val baselineChanged: Boolean = false,
) {
    val isEmpty: Boolean get() = definitionChanged.isEmpty() && sarifChanged.isEmpty() && !projectChanged && !baselineChanged
}

private val HIDDEN_DIRECTORIES: Set<String> = setOf(".git", ".gradle", ".idea")

private const val BUILD_DIRECTORY: String = "build"

private const val SOURCES_DIRECTORY: String = "src"

private val BUILD_SCRIPTS: Set<String> = setOf("build.gradle.kts", "build.gradle")

internal fun classifyChanges(
    paths: List<Path>,
    modules: List<WatchedModule>,
    projectRoots: List<Path>,
    baselines: Set<Path> = emptySet(),
    isOwnWrite: (Path) -> Boolean,
): FileChanges {
    val definition = LinkedHashSet<ModuleId>()
    val testSources = LinkedHashSet<ModuleId>()
    val sarif = LinkedHashSet<ModuleId>()
    var project = false
    var baseline = false
    for (path in paths) {
        if (path in baselines) baseline = true
        val reports = modules.firstOrNull { isUnderBuild(path, it.module) }
        if (reports != null) {
            if (isSarifChange(path, reports.module)) sarif += reports.module.id
            continue
        }
        if (projectRoots.any { path.startsWith(it) && !isOutput(it.relativize(path)) }) project = true
        if (isOwnWrite(path)) continue
        for (watched in modules) {
            val directory = watched.module.directory
            val inSources = path.startsWith(directory.resolve("src"))
            val isBuildScript = path.parent == directory && path.fileName.toString() in BUILD_SCRIPTS
            if (!inSources && !isBuildScript) continue
            definition += watched.module.id
            val testRoots = watched.testRoots.ifEmpty { listOf(directory.resolve("src/test")) }
            if (inSources && testRoots.any { path.startsWith(it) }) testSources += watched.module.id
        }
    }
    return FileChanges(definition, testSources, sarif, project, baseline)
}

private fun isUnderBuild(path: Path, module: KatachiModule): Boolean = path.startsWith(module.directory.resolve("build"))

private fun isSarifChange(path: Path, module: KatachiModule): Boolean {
    val reports = module.directory.resolve(REPORTS)
    val name = path.fileName?.toString().orEmpty()
    return when {
        reports.startsWith(path) -> true
        !path.startsWith(reports) -> false
        name.endsWith(".sarif") -> true
        // A task's directory (`build/reports/katachi/test`): a name without an extension.
        else -> path.parent == reports && '.' !in name
    }
}

private fun isOutput(relative: Path): Boolean {
    var inSources = false
    for (segment in relative) {
        val name = segment.toString()
        when {
            name in HIDDEN_DIRECTORIES -> return true
            name == SOURCES_DIRECTORY -> inSources = true
            name == BUILD_DIRECTORY && !inSources -> return true
        }
    }
    return false
}

internal const val REPORTS: String = "build/reports/katachi"
