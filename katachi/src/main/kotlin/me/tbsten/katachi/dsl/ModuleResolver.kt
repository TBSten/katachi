package me.tbsten.katachi.dsl

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.KatachiInternalException

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
 * walks the tree looking for build files (see [me.tbsten.katachi.scan.discoverModules]), and that stays convention
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

    /**
     * Where the [ModuleResolver]s katachi ships with live.
     *
     * ## Example 1: name the default resolver explicitly
     * ```kt
     * val projectArchitecture = architecture {
     *     moduleResolver = ModuleResolver.Conventional
     *     "domain".group { "UseCase" { } }
     * }
     * ```
     */
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

/**
 * One module a layout key named, with where it lives and what its wildcards captured.
 *
 * ## Example 1: read where a matched module lives
 * ```kt
 * import me.tbsten.katachi.fs.RealFileSystem
 * import me.tbsten.katachi.scan.moduleIndex
 *
 * val fileSystem = RealFileSystem()
 * val index = moduleIndex(fileSystem, fileSystem.workingDirectory)
 * val featureModules: List<ResolvedModule> = index.matching(ModulePattern.compile(":feature:*"))
 * featureModules.forEach { println("${it.path} -> ${it.directory}") }
 * ```
 */
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
 * One evaluation of a `module { }` block: the key it stands for, where its declarations hang,
 * and what `wildcards` reads as while the block runs.
 *
 * A [ResolvedModule] as the layout DSL needs it, plus the one case that is not a module at
 * all — a wildcard key an unresolved [ModuleIndex] kept as itself. See [ModuleIndex.targetsOf].
 */
internal class ModuleTarget(
    /** `":feature:home"`, or `":feature:*"` for a key that was left as a pattern. */
    val modulePath: String,
    /** Relative to the project root, `/` separated. Empty for the root project. */
    val directory: String,
    /** What `wildcards` reads as inside the block. */
    val wildcards: List<String>,
)

/**
 * The modules of a project, found once, so that every layout key is expanded against the
 * same list rather than walking the tree again.
 *
 * Built by [me.tbsten.katachi.scan.moduleIndex] — or by [unresolved] when no file system is at hand, which says
 * something different and which [targetsOf] answers differently.
 *
 * ## Example 1: build the index once and reuse it for every layout key
 * ```kt
 * import me.tbsten.katachi.fs.RealFileSystem
 * import me.tbsten.katachi.scan.moduleIndex
 *
 * val fileSystem = RealFileSystem()
 * val index: ModuleIndex = moduleIndex(fileSystem, fileSystem.workingDirectory)
 * ```
 */
@InternalKatachiApi
public class ModuleIndex internal constructor(
    /** How a module path becomes a directory. */
    public val resolver: ModuleResolver,
    /**
     * The modules found below the project root, or `null` when nobody has looked.
     *
     * The two are not the same answer, and keeping them apart is the whole reason this is
     * nullable. An empty list says the project holds no module; `null` says the question was
     * never asked. Branching on emptiness instead would make a reader that never walked the
     * tree agree with a real project that happens to have no feature module, and the wildcard
     * keys of the definition would vanish either way.
     */
    private val discovered: List<ModulePath>?,
) {
    /**
     * Every module found below the project root, outermost first and siblings by name. Empty
     * for an [unresolved] index, where [isResolved] is what tells the two apart.
     *
     * ## Example 1: list every module the search found
     * ```kt
     * import me.tbsten.katachi.fs.RealFileSystem
     * import me.tbsten.katachi.scan.moduleIndex
     *
     * val fileSystem = RealFileSystem()
     * val index = moduleIndex(fileSystem, fileSystem.workingDirectory)
     * index.modules.forEach { module -> println(module.value) }
     * ```
     */
    public val modules: List<ModulePath> get() = discovered.orEmpty()

    /**
     * Whether the project has been listed.
     *
     * `false` only for an [unresolved] index. One built by [me.tbsten.katachi.scan.moduleIndex] is resolved even
     * when the project turned out to hold no module at all: that emptiness is an answer.
     *
     * ## Example 1: branch on whether the project has been walked yet
     * ```kt
     * import me.tbsten.katachi.fs.RealFileSystem
     * import me.tbsten.katachi.scan.moduleIndex
     *
     * val fileSystem = RealFileSystem()
     * val index = moduleIndex(fileSystem, fileSystem.workingDirectory)
     * if (index.isResolved) println("${index.modules.size} modules found")
     * ```
     */
    public val isResolved: Boolean get() = discovered != null

    /**
     * Where [module] lives, whether or not it exists.
     *
     * A module the layout names but the tree does not hold still resolves: the declaration
     * below it then reports the build file it requires as missing, which says more than
     * "this module is not here" would.
     *
     * ## Example 1: locate a module by its path, without walking the tree
     * ```kt
     * val index = ModuleIndex.unresolved(ModuleResolver.Conventional)
     * index.resolve(ModulePath.of(":core:data")).directory // "core/data"
     * ```
     */
    public fun resolve(module: ModulePath, wildcards: List<String> = emptyList()): ResolvedModule =
        ResolvedModule(
            path = module,
            directory = normalizeDirectory(resolver.directoryOf(module)),
            wildcards = wildcards,
        )

    /**
     * Every existing module [pattern] matches, in [modules] order.
     *
     * ## Example 1: list the modules a wildcard key matches
     * ```kt
     * import me.tbsten.katachi.fs.RealFileSystem
     * import me.tbsten.katachi.scan.moduleIndex
     *
     * val fileSystem = RealFileSystem()
     * val index = moduleIndex(fileSystem, fileSystem.workingDirectory)
     * index.matching(ModulePattern.compile(":feature:*")).map { it.path }
     * ```
     */
    public fun matching(pattern: ModulePattern): List<ResolvedModule> =
        modules.mapNotNull { module -> pattern.match(module)?.let { resolve(module, it) } }

    /**
     * The modules a layout key stands for.
     *
     * A key with a wildcard stands for the modules that exist and match, and for nothing at
     * all when none does — which is why such a key is never missing. A key without one
     * stands for exactly the module it names, existing or not, so that a module someone
     * deleted is reported rather than silently dropped.
     *
     * ## Example 1: expand a layout key the way `module { }` does
     * ```kt
     * import me.tbsten.katachi.fs.RealFileSystem
     * import me.tbsten.katachi.scan.moduleIndex
     *
     * val fileSystem = RealFileSystem()
     * val index = moduleIndex(fileSystem, fileSystem.workingDirectory)
     * index.expand(ModulePattern.compile(":feature:*")).map { it.directory }
     * ```
     */
    public fun expand(pattern: ModulePattern): List<ResolvedModule> =
        if (pattern.hasWildcard) {
            matching(pattern)
        } else {
            val literalPath = pattern.literalPath
                ?: throw KatachiUnresolvableModulePatternException(pattern.pattern)
            listOf(resolve(literalPath))
        }

    /**
     * Where a layout key's `module { }` block is evaluated, and with what.
     *
     * [expand] answers this for a project that has been listed. It cannot answer it for a
     * wildcard key against an [unresolved] index: "no module matches `:feature:*`" and "nobody
     * has looked" would both come out as an empty list, and a caller that reads only the
     * declarations — documentation generation,
     * [me.tbsten.katachi.processor.ProjectModel.declaredEntries] — would quietly lose every
     * declaration such a key makes. So an unresolved index keeps the key as itself: one
     * target whose directory is [ModulePattern.conventionalDirectory], the pattern with its
     * wildcards still in it, and whose wildcards are [ModulePattern.wildcardPlaceholders].
     *
     * The two are deliberately spelled differently. The directory keeps the real `*`, because
     * it names levels that exist and because a path holding a wildcard is what keeps every
     * declaration below it from being [me.tbsten.katachi.dsl.LayoutEntry.required]. A name a
     * block *builds* out of a capture has nothing to match against, so it gets a placeholder
     * instead — one that no amount of string building can turn into a broken glob.
     *
     * The check never takes that branch. It builds its index by walking the project, so a
     * wildcard key expands to the modules that exist, down to none of them.
     */
    internal fun targetsOf(pattern: ModulePattern): List<ModuleTarget> =
        if (pattern.hasWildcard && discovered == null) {
            listOf(
                ModuleTarget(
                    modulePath = pattern.pattern,
                    directory = pattern.conventionalDirectory,
                    wildcards = pattern.wildcardPlaceholders,
                ),
            )
        } else {
            expand(pattern).map { module ->
                ModuleTarget(
                    modulePath = module.path.value,
                    directory = module.directory,
                    wildcards = module.wildcards,
                )
            }
        }

    override fun toString(): String = when (discovered) {
        null -> "ModuleIndex(unresolved, $resolver)"
        else -> "ModuleIndex(${discovered.size} modules, $resolver)"
    }

    /**
     * Where an index that has not walked the project comes from.
     *
     * ## Example 1: evaluate a definition without touching the file system
     * ```kt
     * val definition = architecture { "domain".group { "UseCase" { } } }
     * val entries = definition.flattenLayout(ModuleIndex.unresolved(definition.moduleResolver))
     * ```
     */
    public companion object {
        /**
         * An index for a project nobody has listed, which is what a layout read without a
         * file system is flattened against.
         *
         * It still resolves a key naming one module — where `:core:data` lives is [resolver]'s
         * answer and needs no tree — while a key with a wildcard is kept as the pattern it was
         * written as. See [targetsOf].
         *
         * ## Example 1: a literal key still resolves, a wildcard key does not
         * ```kt
         * val index = ModuleIndex.unresolved(ModuleResolver.Conventional)
         * index.resolve(ModulePath.of(":app")).directory // "app"
         * index.matching(ModulePattern.compile(":feature:*")) // always empty; nothing has been listed
         * ```
         */
        public fun unresolved(resolver: ModuleResolver): ModuleIndex =
            ModuleIndex(resolver = resolver, discovered = null)
    }
}


/**
 * Cleans up what a replaced [ModuleResolver] returned: a leading or trailing `/`, a `\`, a
 * repeated separator or a `.` segment all mean the same directory, and katachi compares
 * layout paths as strings.
 */
private fun normalizeDirectory(directory: String): String =
    directory.split('/', '\\').filter { it.isNotEmpty() && it != "." }.joinToString("/")
