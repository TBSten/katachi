package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiUnresolvableModulePatternException
import me.tbsten.katachi.dsl.ModulePath
import me.tbsten.katachi.dsl.ModuleResolver

/** One module a layout key named, with where it lives and what its wildcards captured. */
internal class ResolvedModule(
    /** The module itself, `:feature:home`. */
    val path: ModulePath,
    /**
     * The module's directory relative to the project root, `/` separated. Empty for the root
     * project, whose directory *is* the project root.
     */
    val directory: String,
    /**
     * What the pattern captured, in the order the wildcards were written. Empty for a key
     * that holds no wildcard. See [ModulePattern.match].
     */
    val wildcards: List<String>,
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
 * Built by [me.tbsten.katachi.scan.internal.moduleIndex] — or by [unresolved] when no file system is at hand, which says
 * something different and which [targetsOf] answers differently.
 *
 * Public only because [me.tbsten.katachi.scan.internal.moduleIndex] hands it to katachi's own samples,
 * which pass it straight on to [flattenLayout]. None of its members are.
 *
 * ## Example 1: build the index once and reuse it for every layout key
 * ```kt
 * import me.tbsten.katachi.fs.internal.RealFileSystem
 * import me.tbsten.katachi.scan.internal.moduleIndex
 *
 * val fileSystem = RealFileSystem()
 * val index: ModuleIndex = moduleIndex(fileSystem, fileSystem.workingDirectory)
 * ```
 */
@InternalKatachiApi
public class ModuleIndex internal constructor(
    /** How a module path becomes a directory. */
    private val resolver: ModuleResolver,
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
     * for an [unresolved] index too, which is why [targetsOf] asks `discovered` instead.
     */
    private val modules: List<ModulePath> get() = discovered.orEmpty()

    /**
     * Where [module] lives, whether or not it exists.
     *
     * A module the layout names but the tree does not hold still resolves: the declaration
     * below it then reports the build file it requires as missing, which says more than
     * "this module is not here" would.
     */
    internal fun resolve(module: ModulePath, wildcards: List<String> = emptyList()): ResolvedModule =
        ResolvedModule(
            path = module,
            directory = normalizeDirectory(resolver.directoryOf(module)),
            wildcards = wildcards,
        )

    /** Every existing module [pattern] matches, in [modules] order. */
    private fun matching(pattern: ModulePattern): List<ResolvedModule> =
        modules.mapNotNull { module -> pattern.match(module)?.let { resolve(module, it) } }

    /**
     * The modules a layout key stands for.
     *
     * A key with a wildcard stands for the modules that exist and match, and for nothing at
     * all when none does — which is why such a key is never missing. A key without one
     * stands for exactly the module it names, existing or not, so that a module someone
     * deleted is reported rather than silently dropped.
     */
    internal fun expand(pattern: ModulePattern): List<ResolvedModule> =
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
     * [me.tbsten.katachi.processor.ArchitectureProcessContext.declaredEntries] — would quietly lose every
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

    /** Where an index that has not walked the project comes from. */
    internal companion object {
        /**
         * An index for a project nobody has listed, which is what a layout read without a
         * file system is flattened against.
         *
         * It still resolves a key naming one module — where `:core:data` lives is [resolver]'s
         * answer and needs no tree — while a key with a wildcard is kept as the pattern it was
         * written as. See [targetsOf].
         */
        fun unresolved(resolver: ModuleResolver): ModuleIndex =
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
