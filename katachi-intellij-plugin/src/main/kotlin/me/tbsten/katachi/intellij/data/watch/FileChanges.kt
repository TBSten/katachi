package me.tbsten.katachi.intellij.data.watch

import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import java.nio.file.Path

/** A definition module as the file watcher sees it: its directory and the test source roots its definition lives in. */
internal data class WatchedModule(
    val module: KatachiModule,
    /** The module's test source roots (design 4.6); empty when the IDE knows none, and then `src/test` stands in. */
    val testRoots: List<Path>,
)

/**
 * What a batch of file changes means to the plugin (design 4.8, 4.7): which definitions changed, which of
 * them in their test sources (what reloads by itself), which modules got a new SARIF, and whether the
 * project under a definition changed at all (the check's results go stale).
 */
internal data class FileChanges(
    /** A file under the module's `src/`, or its build script. */
    val definitionChanged: Set<ModuleId> = emptySet(),
    /** A file under the module's test source roots: a candidate for the automatic reload. */
    val testSourcesChanged: Set<ModuleId> = emptySet(),
    /** A `*.sarif` under the module's `build/reports/katachi/`. */
    val sarifChanged: Set<ModuleId> = emptySet(),
    /** A file under a project root, outside `build/` (not a package of the sources), `.git/`, `.gradle/` and `.idea/`. */
    val projectChanged: Boolean = false,
) {
    val isEmpty: Boolean get() = definitionChanged.isEmpty() && sarifChanged.isEmpty() && !projectChanged
}

/**
 * Directory names whose content is never the definition nor the checked project, wherever they are: Gradle's and
 * the IDE's own, and git's (the git plugin updates `.git/index`, `HEAD` and `logs/` at every commit, branch switch
 * and background fetch). The same ones katachi's walk does not see (design 4.4 `NotWalked`).
 */
private val HIDDEN_DIRECTORIES: Set<String> = setOf(".git", ".gradle", ".idea")

/** Gradle's build output, `build/`, counts only outside the sources: `src/main/kotlin/com/example/build/Foo.kt` is a source. */
private const val BUILD_DIRECTORY: String = "build"

private const val SOURCES_DIRECTORY: String = "src"

private val BUILD_SCRIPTS: Set<String> = setOf("build.gradle.kts", "build.gradle")

/**
 * Sorts [paths] (the files of one batch of VFS events) into [FileChanges]. Only string work on paths: no
 * disk, no VFS, as an `AsyncFileListener` has to be fast (design 5 "ファイル監視"). A path under an output
 * directory is dropped first, the SARIF reports excepted. A file the plugin is writing itself ([isOwnWrite],
 * a generation's output) is not a definition change, but it does change the project.
 *
 * ```kotlin
 * val changes = classifyChanges(paths, listOf(WatchedModule(module, testRoots)), listOf(projectRoot)) { false }
 * if (module.id in changes.testSourcesChanged) scheduleReload()
 * ```
 */
internal fun classifyChanges(
    paths: List<Path>,
    modules: List<WatchedModule>,
    projectRoots: List<Path>,
    isOwnWrite: (Path) -> Boolean,
): FileChanges {
    val definition = LinkedHashSet<ModuleId>()
    val testSources = LinkedHashSet<ModuleId>()
    val sarif = LinkedHashSet<ModuleId>()
    var project = false
    for (path in paths) {
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
    return FileChanges(definition, testSources, sarif, project)
}

private fun isUnderBuild(path: Path, module: KatachiModule): Boolean = path.startsWith(module.directory.resolve("build"))

/**
 * Whether [path] (under the module's `build/`) may have changed its SARIF reports: a `*.sarif` under
 * `build/reports/katachi/`, or a directory on the way to one, created or deleted whole (the VFS reports the
 * topmost directory that appears or goes, and the test task deletes `build/reports/katachi/` first).
 */
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

/** Whether [relative] goes through `.git/`, `.gradle/` or `.idea/`, or through a `build/` that comes before any `src/`. */
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

/** Where katachi's `report { sarif = true }` writes, relative to the module (design 4.7). */
internal const val REPORTS: String = "build/reports/katachi"
