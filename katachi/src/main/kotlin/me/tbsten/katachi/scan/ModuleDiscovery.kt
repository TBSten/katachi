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
 *
 * A directory the search could not read is dropped here, along with the modules below it.
 * [scanModules] is the same search with those directories kept, which is what the check runs
 * so that it can report them instead of quietly answering with fewer modules.
 */
@InternalKatachiApi
public fun moduleIndex(
    fileSystem: KatachiFileSystem,
    projectRoot: FsPath,
    resolver: ModuleResolver = ModuleResolver.Conventional,
): ModuleIndex = scanModules(fileSystem, projectRoot).indexWith(resolver)

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
 *
 * A directory that throws is skipped rather than allowed to end the search, and what was lost
 * with it is dropped by this overload. See [scanModules] for the answer that keeps it.
 */
@InternalKatachiApi
public fun discoverModules(fileSystem: KatachiFileSystem, projectRoot: FsPath): List<ModulePath> =
    scanModules(fileSystem, projectRoot).modules

/**
 * Everything one search of the tree produced.
 *
 * The two halves are the same walk seen from either side: the modules it did find, and the
 * directories it could not read while finding them. Keeping the second half is what stops a
 * failure here from looking like "this project has fewer modules than it has" — a wildcard
 * module key would then expand to fewer directories and the ones it missed would come back as
 * `[UnexpectedDirectory]`, with nothing in the report saying why.
 */
internal class ModuleScan(
    /** The modules found, outermost first and siblings by name. */
    val modules: List<ModulePath>,
    /**
     * Directories the search could not read, in search order.
     *
     * Already shaped as the blocks they become, so the walk only has to hand them on, and so
     * that the one place that knows *why* nothing is known is the place that failed.
     */
    val unchecked: List<UncheckedDirectory>,
)

/** [this]'s modules as an index. The one place a resolved [ModuleIndex] is built. */
internal fun ModuleScan.indexWith(resolver: ModuleResolver): ModuleIndex =
    ModuleIndex(resolver = resolver, discovered = modules)

/** [discoverModules], with the directories it could not read kept rather than dropped. */
internal fun scanModules(fileSystem: KatachiFileSystem, projectRoot: FsPath): ModuleScan {
    val search = ModuleSearch(fileSystem)
    search.collect(projectRoot, "", ModulePath.ROOT)
    return ModuleScan(modules = search.modules, unchecked = search.unchecked)
}

private val BUILD_FILE_NAMES = listOf("build.gradle.kts", "build.gradle")

private val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")

private val NEVER_WALKED = setOf("build", "buildSrc", "src")

/**
 * The search, cut into pieces that can fail on their own.
 *
 * The same rule the walk in `Scan.kt` follows, one layer earlier: a directory that cannot be
 * read costs that directory and the modules below it, and nothing else. A sibling subtree
 * answers "which modules are in here" without needing this one, so there is no reason for one
 * unreadable directory to leave the whole project looking module-less.
 */
private class ModuleSearch(private val fileSystem: KatachiFileSystem) {
    val modules = mutableListOf<ModulePath>()

    val unchecked = mutableListOf<UncheckedDirectory>()

    /**
     * Whether [directory] is a module, and what is below it.
     *
     * The two questions share one [catching] because both are about this directory: neither a
     * build file that cannot be looked up nor a listing that fails leaves anything to go on
     * here, while the parent still has its other children to search.
     */
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
