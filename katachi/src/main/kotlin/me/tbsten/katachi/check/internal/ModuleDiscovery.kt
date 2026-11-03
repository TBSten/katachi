package me.tbsten.katachi.check.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UncheckedDirectory
import me.tbsten.katachi.check.UncheckedDirectoryReason
import me.tbsten.katachi.dsl.ModulePath
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.OutsideProjectDirectories
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.internal.catching

/**
 * Finds the project's modules and pairs them with [resolver].
 *
 * The index is resolved even when the project holds no module. A directory the search could
 * not read is dropped, along with the modules below it.
 *
 * ## Example 1: flatten a layout against the modules that really exist
 * ```kt
 * val fileSystem = RealFileSystem()
 * val index = moduleIndex(fileSystem, findProjectRoot(fileSystem).path, projectArchitecture.moduleResolver)
 * val entries = projectArchitecture.flattenLayout(index)
 * ```
 */
@InternalKatachiApi
public fun moduleIndex(
    fileSystem: KatachiFileSystem,
    projectRoot: FsPath,
    resolver: ModuleResolver = ModuleResolver.Conventional,
): ModuleIndex = scanModules(fileSystem, projectRoot).indexWith(resolver)

// A module is a directory with build.gradle(.kts): settings files cannot be read without evaluating them.
// Non-module directories are still descended into (`:app:android` is a project, `app` only a folder).
internal fun discoverModules(fileSystem: KatachiFileSystem, projectRoot: FsPath): List<ModulePath> =
    scanModules(fileSystem, projectRoot).modules

internal class ModuleScan(
    val modules: List<ModulePath>,
    val unchecked: List<UncheckedDirectory>,
)

internal fun ModuleScan.indexWith(resolver: ModuleResolver): ModuleIndex =
    ModuleIndex(resolver = resolver, discovered = modules)

internal fun scanModules(
    fileSystem: KatachiFileSystem,
    projectRoot: FsPath,
    skips: (FsPath) -> Boolean = { false },
): ModuleScan {
    val search = ModuleSearch(fileSystem, skips)
    search.collect(projectRoot, "", ModulePath.ROOT)
    return ModuleScan(modules = search.modules, unchecked = search.unchecked)
}

private val BUILD_FILE_NAMES = listOf("build.gradle.kts", "build.gradle")

private val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")

private val NEVER_WALKED = setOf("build", "buildSrc", "src")

private class ModuleSearch(
    private val fileSystem: KatachiFileSystem,
    private val skips: (FsPath) -> Boolean,
) {
    val modules = mutableListOf<ModulePath>()

    val unchecked = mutableListOf<UncheckedDirectory>()

    fun collect(directory: FsPath, path: String, module: ModulePath) {
        val children = catching {
            if (BUILD_FILE_NAMES.any { fileSystem.exists(directory / it) }) modules += module
            fileSystem.list(directory)
        }.getOrElse { cause ->
            report(path, cause)
            return
        }
        for (child in children) {
            // One child at a time, the way the walk does it: what a failure here costs is this
            // one subtree.
            catching { visit(child, childPathOf(path, child), module) }
                .onFailure { cause -> report(childPathOf(path, child), cause) }
        }
    }

    private fun visit(child: FsPath, path: String, module: ModulePath) {
        val name = child.name
        if (name.startsWith(".") || name in NEVER_WALKED) return
        // Before `isDirectory`, so that a skipped child costs no file system call at all.
        if (skips(child)) return
        if (!fileSystem.isDirectory(child)) return
        // A settings file makes this the root of another build. Its projects are that
        // build's, so neither it nor anything below it is a module here.
        if (SETTINGS_FILE_NAMES.any { fileSystem.exists(child / it) }) return
        collect(child, path, module.child(name))
    }

    private fun childPathOf(path: String, child: FsPath): String =
        if (path.isEmpty()) child.name else "$path/${child.name}"

    private fun report(path: String, cause: Throwable) {
        unchecked += UncheckedDirectory(
            path = path.ifEmpty { ROOT_PATH },
            reason = UncheckedDirectoryReason.ModulesNotDiscovered,
            cause = cause,
        )
    }
}
