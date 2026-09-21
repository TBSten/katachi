package me.tbsten.katachi.scan

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.ModuleIndex
import me.tbsten.katachi.dsl.ModulePath
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.fs.FsPath
import me.tbsten.katachi.fs.KatachiFileSystem

/**
 * Finds the project's modules and pairs them with [resolver].
 *
 * The index this returns is resolved even when the project holds no module: the tree was
 * walked, and what it holds is the answer. [ModuleIndex.unresolved] is the other case.
 */
@InternalKatachiApi
public fun moduleIndex(
    fileSystem: KatachiFileSystem,
    projectRoot: FsPath,
    resolver: ModuleResolver = ModuleResolver.Conventional,
): ModuleIndex = ModuleIndex(
    resolver = resolver,
    discovered = discoverModules(fileSystem, projectRoot),
)

/**
 * Every module below [projectRoot], found by looking for build files.
 *
 * A directory is a module when it holds a `build.gradle.kts` or a `build.gradle`. That is
 * the one thing every Gradle project has — `settings.gradle.kts` is not readable without
 * evaluating it, and a directory name alone says nothing. A directory that is not a module
 * is still walked into, because `:app:android` is a project while `app` is only a folder.
 *
 * Three kinds of directory are left alone:
 *
 * - anything whose name starts with `.`, and `build`, `buildSrc` and `src`. None of them
 *   ever holds a module of this build, and walking `src` of every module is the bulk of the
 *   work otherwise;
 * - a directory holding a settings file, which makes it a separate build. Its projects
 *   belong to that build, not this one, so an included build such as `buildLogic` — or a
 *   sample project sitting inside a library's own repository — contributes no module here.
 *
 * Discovery is convention based and does not consult the [ModuleResolver]: a resolver maps a
 * module path to a directory, and that mapping cannot be run backwards.
 */
@InternalKatachiApi
public fun discoverModules(fileSystem: KatachiFileSystem, projectRoot: FsPath): List<ModulePath> {
    val modules = mutableListOf<ModulePath>()
    collectModules(fileSystem, projectRoot, ModulePath.ROOT, modules)
    return modules
}

private val BUILD_FILE_NAMES = listOf("build.gradle.kts", "build.gradle")

private val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")

private val NEVER_WALKED = setOf("build", "buildSrc", "src")

private fun collectModules(
    fileSystem: KatachiFileSystem,
    directory: FsPath,
    module: ModulePath,
    into: MutableList<ModulePath>,
) {
    if (BUILD_FILE_NAMES.any { fileSystem.exists(directory / it) }) into += module
    for (child in fileSystem.list(directory)) {
        val name = child.name
        if (name.startsWith(".") || name in NEVER_WALKED) continue
        if (!fileSystem.isDirectory(child)) continue
        // A settings file makes this the root of another build. Its projects are that
        // build's, so neither it nor anything below it is a module here.
        if (SETTINGS_FILE_NAMES.any { fileSystem.exists(child / it) }) continue
        collectModules(fileSystem, child, module.child(name), into)
    }
}
