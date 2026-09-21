package me.tbsten.katachi.check

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiInternalException

/**
 * A module pattern with no wildcard in it named no module.
 *
 * @property pattern the pattern that was being expanded.
 *
 * ## Example 1: report it instead of treating it as a bad definition
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiUnresolvableModulePatternException) {
 *     println("katachi bug, pattern was `${cause.pattern}`. Please report it.")
 * }
 * ```
 */
public class KatachiUnresolvableModulePatternException internal constructor(
    public val pattern: String,
) : KatachiInternalException(
    message = """
        The module pattern `$pattern` holds no wildcard, yet it names no single module.
        A pattern without a wildcard always names exactly one, so a layout cannot cause
        this. Please report it at https://github.com/TBSten/katachi/issues.
    """.trimIndent(),
)

/**
 * Turns a Gradle module path into the directory that holds it.
 *
 * This is the only thing that is replaceable, and it does one thing: module path in,
 * directory out. Nothing else about a module goes through here — a source set is the plain
 * directory `src/<name>`, and a package directory is derived by whatever strategy the user
 * assigned to their own `modulePackage`. Keeping those out is what lets v0.1 resolve modules
 * without reading anything from Gradle.
 *
 * The default, [Conventional], replaces `:` with `/`. That is right for every project that
 * has not customised `projectDir`, which in practice means every new project. A build that
 * has customised it replaces the resolver. A resolver replaced this way is asked about the
 * modules the layout names. It is not asked which modules exist: expanding `":feature:*"`
 * walks the tree looking for build files (see [discoverModules]), and that stays convention
 * based until a Gradle plugin can hand katachi the real list.
 *
 * ## Example 1: place a module at a non-conventional directory
 * ```kt
 * val projectArchitecture = architecture {
 *   moduleResolver = ModuleResolver { module ->
 *     if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
 *   }
 * }
 * ```
 */
public fun interface ModuleResolver {
    /**
     * Where [module] lives, written relative to the project root and `/` separated.
     *
     * The empty string means the project root itself, which is what the root project
     * resolves to.
     *
     * ## Example 1: check the default conversion
     * ```kt
     * ModuleResolver.Conventional.directoryOf(ModulePath.of(":core:data")) shouldBe "core/data"
     * ```
     */
    public fun directoryOf(module: ModulePath): String

    public companion object {
        /**
         * The default: `:` becomes `/`, so `:core:data` is `core/data` and `":"` is the
         * project root.
         *
         * ## Example 1: resolve the root project
         * ```kt
         * ModuleResolver.Conventional.directoryOf(ModulePath.ROOT) shouldBe ""
         * ```
         */
        public val Conventional: ModuleResolver = ConventionalModuleResolver
    }
}

private object ConventionalModuleResolver : ModuleResolver {
    override fun directoryOf(module: ModulePath): String = module.segments.joinToString("/")

    override fun toString(): String = "ModuleResolver.Conventional"
}

/** One module a layout key named, with where it lives and what its wildcards captured. */
@InternalKatachiApi
public class ResolvedModule internal constructor(
    /** The module itself, `:feature:home`. */
    public val path: ModulePath,
    /**
     * The module's directory relative to the project root, `/` separated. Empty for the root
     * project, whose directory *is* the project root.
     */
    public val directory: String,
    /**
     * What the pattern captured, in the order the wildcards were written. Empty for a key
     * that holds no wildcard. See [ModulePattern.match].
     */
    public val wildcards: List<String>,
) {
    override fun toString(): String =
        "ResolvedModule($path -> ${directory.ifEmpty { "<root>" }}, wildcards=$wildcards)"
}

/**
 * The modules of a project, found once, so that every layout key is expanded against the
 * same list rather than walking the tree again.
 *
 * Built by [moduleIndex].
 */
@InternalKatachiApi
public class ModuleIndex internal constructor(
    /** How a module path becomes a directory. */
    public val resolver: ModuleResolver,
    /** Every module found below the project root, outermost first and siblings by name. */
    public val modules: List<ModulePath>,
) {
    /**
     * Where [module] lives, whether or not it exists.
     *
     * A module the layout names but the tree does not hold still resolves: the declaration
     * below it then reports the build file it requires as missing, which says more than
     * "this module is not here" would.
     */
    public fun resolve(module: ModulePath, wildcards: List<String> = emptyList()): ResolvedModule =
        ResolvedModule(
            path = module,
            directory = normalizeDirectory(resolver.directoryOf(module)),
            wildcards = wildcards,
        )

    /** Every existing module [pattern] matches, in [modules] order. */
    public fun matching(pattern: ModulePattern): List<ResolvedModule> =
        modules.mapNotNull { module -> pattern.match(module)?.let { resolve(module, it) } }

    /**
     * The modules a layout key stands for.
     *
     * A key with a wildcard stands for the modules that exist and match, and for nothing at
     * all when none does — which is why such a key is never missing. A key without one
     * stands for exactly the module it names, existing or not, so that a module someone
     * deleted is reported rather than silently dropped.
     */
    public fun expand(pattern: ModulePattern): List<ResolvedModule> =
        if (pattern.hasWildcard) {
            matching(pattern)
        } else {
            val literalPath = pattern.literalPath
                ?: throw KatachiUnresolvableModulePatternException(pattern.pattern)
            listOf(resolve(literalPath))
        }

    override fun toString(): String = "ModuleIndex(${modules.size} modules, $resolver)"
}

/** Finds the project's modules and pairs them with [resolver]. */
@InternalKatachiApi
public fun moduleIndex(
    fileSystem: KatachiFileSystem,
    projectRoot: FsPath,
    resolver: ModuleResolver = ModuleResolver.Conventional,
): ModuleIndex = ModuleIndex(resolver, discoverModules(fileSystem, projectRoot))

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

/**
 * Cleans up what a replaced [ModuleResolver] returned: a leading or trailing `/`, a `\`, a
 * repeated separator or a `.` segment all mean the same directory, and katachi compares
 * layout paths as strings.
 */
private fun normalizeDirectory(directory: String): String =
    directory.split('/', '\\').filter { it.isNotEmpty() && it != "." }.joinToString("/")
