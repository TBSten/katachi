package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiUnresolvableModulePatternException
import me.tbsten.katachi.dsl.ModulePath
import me.tbsten.katachi.dsl.ModuleResolver

internal class ResolvedModule(
    val path: ModulePath,
    val directory: String,
    val wildcards: List<String>,
) {
    override fun toString(): String =
        "ResolvedModule($path -> ${directory.ifEmpty { "<root>" }}, wildcards=$wildcards)"
}

// Includes the non-module case: a wildcard key an unresolved ModuleIndex kept as itself.
internal class ModuleTarget(
    val modulePath: String,
    val directory: String,
    val wildcards: List<String>,
)

/**
 * The modules of a project, found once, so every wildcard module path in a layout is expanded against the same list.
 *
 * Build one with [me.tbsten.katachi.check.internal.moduleIndex] and pass it to [flattenLayout].
 *
 * ## Example 1: build the index once and flatten a layout against it
 * ```kt
 * import me.tbsten.katachi.check.internal.moduleIndex
 *
 * val index: ModuleIndex = moduleIndex(fileSystem, fileSystem.workingDirectory)
 * val entries = projectArchitecture.flattenLayout(index)
 * ```
 */
@InternalKatachiApi
public class ModuleIndex internal constructor(
    internal val resolver: ModuleResolver,
    // null (never listed) differs from empty (no modules): branching on emptiness would make an unlisted index
    // agree with a project lacking feature modules.
    private val discovered: List<ModulePath>?,
    private val onWildcardKey: (() -> Unit)? = null,
) {
    // Empty for an unresolved index too, which is why targetsOf asks `discovered`.
    private val modules: List<ModulePath> get() = discovered.orEmpty()

    internal val keepsWildcardKeys: Boolean get() = discovered == null

    // Resolves a module that does not exist so the declaration below reports the missing build file instead.
    internal fun resolve(module: ModulePath, wildcards: List<String> = emptyList()): ResolvedModule =
        ResolvedModule(
            path = module,
            directory = normalizeDirectory(resolver.directoryOf(module)),
            wildcards = wildcards,
        )

    private fun matching(pattern: ModulePattern): List<ResolvedModule> =
        modules.mapNotNull { module -> pattern.match(module)?.let { resolve(module, it) } }

    // A wildcard key matching nothing stands for nothing (never missing); a literal key stands for its module even
    // if absent, so deleted modules are reported.
    internal fun expand(pattern: ModulePattern): List<ResolvedModule> =
        if (pattern.hasWildcard) {
            matching(pattern)
        } else {
            val literalPath = pattern.literalPath
                ?: throw KatachiUnresolvableModulePatternException(pattern.pattern)
            listOf(resolve(literalPath))
        }

    // An unresolved index cannot use expand: no match and nobody looked would both be an empty list.
    internal fun targetsOf(pattern: ModulePattern): List<ModuleTarget> {
        if (pattern.hasWildcard) onWildcardKey?.invoke()
        return if (pattern.hasWildcard && discovered == null) {
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
    }

    // A layout that never expands a wildcard key reads only `resolver`, so one evaluation can serve both
    // declaredEntries and the walk.
    internal fun reportingWildcardKeys(onWildcardKey: () -> Unit): ModuleIndex =
        ModuleIndex(resolver = resolver, discovered = discovered, onWildcardKey = onWildcardKey)

    override fun toString(): String = when (discovered) {
        null -> "ModuleIndex(unresolved, $resolver)"
        else -> "ModuleIndex(${discovered.size} modules, $resolver)"
    }

    internal companion object {
        fun unresolved(resolver: ModuleResolver): ModuleIndex =
            ModuleIndex(resolver = resolver, discovered = null)
    }
}

// A replaced resolver may return a leading or trailing `/`, `\`, repeated separators or `.` segments; layout paths
// are compared as strings.
private fun normalizeDirectory(directory: String): String =
    directory.split('/', '\\').filter { it.isNotEmpty() && it != "." }.joinToString("/")
