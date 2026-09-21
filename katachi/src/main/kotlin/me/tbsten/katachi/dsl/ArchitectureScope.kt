package me.tbsten.katachi.dsl

import me.tbsten.katachi.check.FileSelection
import me.tbsten.katachi.check.ModuleResolver

/**
 * Anything that can hold groups: the root of the DSL and a group itself.
 *
 * ## Example 1: collect groups in an ArchitectureScope extension function
 * ```kt
 * fun ArchitectureScope.domainRoles() {
 *   "domain".group { "UseCase" { } }
 * }
 * ```
 */
@KatachiDsl
public sealed interface GroupContainerScope {
    /**
     * Declares a group. Nest `group` calls to nest groups.
     *
     * Everything a group carries is written inside the block, metadata included. The
     * signature stays a name and a block, so that a processor bringing a new word does not
     * mean a new parameter here.
     *
     * ## Example 1: declare a group
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { }
     *     }
     * }
     * ```
     *
     * ## Example 2: keep a group out of the generated documentation
     * ```kt
     * val arch = architecture {
     *     "build".group {
     *         documented = false
     *         "VersionCatalog" { documented = false }
     *     }
     * }
     * ```
     */
    public fun String.group(block: GroupScope.() -> Unit)
}

/**
 * Receiver of `architecture { }`.
 *
 * It intentionally has no `String.invoke`: a role has to sit in a group, because a group
 * is what decides the role's documentation output directory.
 *
 * ## Example 1: declare groups inside architecture { }
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" { }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface ArchitectureScope : GroupContainerScope {
    /**
     * Which files of the project the check looks at. Defaults to [gitTracked].
     *
     * ## Example 1: walk the whole tree instead of only git-tracked files
     * ```kt
     * val arch = architecture {
     *   files = wholeTree()
     *   "domain".group { "UseCase" { } }
     * }
     * arch.files shouldBe FileSelection.WholeTree
     * ```
     */
    public var files: FileSelection

    /**
     * How a module path written in a `layout { }` becomes a directory. Defaults to
     * [conventionalModuleResolver].
     *
     * ## Example 1: replace the resolution rule for module paths
     * ```kt
     * val architecture = architecture {
     *   moduleResolver = ModuleResolver { module ->
     *     if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
     *   }
     * }
     * ```
     */
    public var moduleResolver: ModuleResolver
}

internal class ArchitectureScopeImpl : ArchitectureScope {
    private val groups = mutableListOf<Group>()
    private val declaredGroupNames = DeclaredNames()

    override var files: FileSelection = FileSelection.GitTracked

    override var moduleResolver: ModuleResolver = ModuleResolver.Conventional

    override fun String.group(block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = this,
            parentPath = emptyList(),
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredGroupNames,
            block = block,
        )
    }

    fun build(): Architecture =
        Architecture(groups = groups.toList(), files = files, moduleResolver = moduleResolver)
}
