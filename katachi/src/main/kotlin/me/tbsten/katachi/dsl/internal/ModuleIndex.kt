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
    /**
     * The names the key gave its `*`s, as [ModuleIndex.targetsOf] was handed them, or `null` for
     * a key written without names.
     */
    val captureNames: List<String>? = null,
)

/**
 * The modules of a project, found once, so that every layout key is expanded against the
 * same list rather than walking the tree again.
 *
 * Built by [me.tbsten.katachi.check.internal.moduleIndex] — or by [unresolved] when no file system is at hand, which says
 * something different and which [targetsOf] answers differently.
 *
 * Public only because [me.tbsten.katachi.check.internal.moduleIndex] hands it to katachi's own samples,
 * which pass it straight on to [flattenLayout]. None of its members are.
 *
 * ## Example 1: build the index once and reuse it for every layout key
 * ```kt
 * import me.tbsten.katachi.dsl.files.internal.RealFileSystem
 * import me.tbsten.katachi.check.internal.moduleIndex
 *
 * val fileSystem = RealFileSystem()
 * val index: ModuleIndex = moduleIndex(fileSystem, fileSystem.workingDirectory)
 * ```
 */
@InternalKatachiApi
public class ModuleIndex internal constructor(
    /** How a module path becomes a directory. */
    internal val resolver: ModuleResolver,
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
    /**
     * Told whenever a layout key with a wildcard is expanded -- the one question whose answer
     * depends on [discovered]. See [reportingWildcardKeys].
     */
    private val onWildcardKey: (() -> Unit)? = null,
    /**
     * The capture values a template generation fills named wildcard keys in with, or `null` for
     * every other reader. See [boundTo].
     */
    private val binding: ModuleBinding? = null,
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
    internal fun targetsOf(pattern: ModulePattern, captureNames: List<String>? = null): List<ModuleTarget> {
        if (pattern.hasWildcard) onWildcardKey?.invoke()
        if (binding != null && pattern.hasWildcard) return boundTargetsOf(binding, pattern, captureNames)
        return if (pattern.hasWildcard && discovered == null) {
            listOf(
                ModuleTarget(
                    modulePath = pattern.pattern,
                    directory = pattern.conventionalDirectory,
                    wildcards = pattern.wildcardPlaceholders,
                    captureNames = captureNames,
                ),
            )
        } else {
            expand(pattern).map { module ->
                ModuleTarget(
                    modulePath = module.path.value,
                    directory = module.directory,
                    wildcards = module.wildcards,
                    captureNames = captureNames,
                )
            }
        }
    }

    /**
     * A wildcard key read against [binding]: the one existing module its named `*`s' values
     * pick, or the key kept as itself when it is unnamed or a value is missing -- exactly as an
     * [unresolved] index keeps it, so that such a key still names no single directory.
     */
    private fun boundTargetsOf(
        binding: ModuleBinding,
        pattern: ModulePattern,
        captureNames: List<String>?,
    ): List<ModuleTarget> {
        val values = captureNames?.map { binding.values[it] ?: return keptAsPattern(pattern, captureNames) }
            ?: return keptAsPattern(pattern, captureNames)
        val matching = matching(pattern)
        // `**` may only be the last level, so the `*`s the names belong to are the first captures.
        val picked = matching.filter { it.wildcards.take(values.size) == values }
        if (picked.isEmpty()) {
            binding.misses += ModuleMiss(
                modulePattern = pattern.pattern,
                captureNames = captureNames.orEmpty(),
                modulePath = pattern.filledIn(values),
                existing = matching.map { it.path.value },
                existingValues = matching.map { it.wildcards.take(values.size) },
            )
        }
        return picked.map { module ->
            ModuleTarget(
                modulePath = module.path.value,
                directory = module.directory,
                wildcards = module.wildcards,
                captureNames = captureNames,
            )
        }
    }

    private fun keptAsPattern(pattern: ModulePattern, captureNames: List<String>?): List<ModuleTarget> = listOf(
        ModuleTarget(
            modulePath = pattern.pattern,
            directory = pattern.conventionalDirectory,
            wildcards = pattern.wildcardPlaceholders,
            captureNames = captureNames,
        ),
    )

    /**
     * This index read by template generation: a key whose `*`s are named, and whose names all
     * have a value in [values], stands for the one existing module those values pick. Every other
     * wildcard key is kept as itself, as an [unresolved] index keeps it.
     *
     * A value naming no existing module leaves that key with no module at all and is noted in
     * [misses] instead of being refused here: which role asked, and whether another of its places
     * still takes the file, is only known to the caller.
     */
    internal fun boundTo(values: Map<String, String>, misses: MutableList<ModuleMiss>): ModuleIndex =
        ModuleIndex(resolver = resolver, discovered = discovered, binding = ModuleBinding(values, misses))

    /**
     * This index, telling [onWildcardKey] each time a layout key with a wildcard is expanded.
     *
     * A layout evaluated without that happening reads nothing but [resolver] from its index, so
     * it comes out the same against any index sharing the resolver -- listed or [unresolved].
     * That is what lets one evaluation of such a role answer both
     * [me.tbsten.katachi.processor.ArchitectureProcessContext.declaredEntries] and the walk.
     */
    internal fun reportingWildcardKeys(onWildcardKey: () -> Unit): ModuleIndex =
        ModuleIndex(resolver = resolver, discovered = discovered, onWildcardKey = onWildcardKey, binding = binding)

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

/** What [ModuleIndex.boundTo] fills named wildcard keys in with, and where it notes a value that picks nothing. */
internal class ModuleBinding(
    val values: Map<String, String>,
    val misses: MutableList<ModuleMiss>,
)

/** A named wildcard key whose values picked no existing module. */
internal data class ModuleMiss(
    /** The key as written, `":feature:*"`. */
    val modulePattern: String,
    /** The names the key gives its `*`s, in order. */
    val captureNames: List<String>,
    /** The key with the values put in, `":feature:hoem"`. */
    val modulePath: String,
    /** Every existing module the key matches, whatever the values. */
    val existing: List<String>,
    /** For each of [existing], the values of [captureNames] that pick it. */
    val existingValues: List<List<String>>,
)
