package me.tbsten.katachi.dsl

import me.tbsten.katachi.fs.FileSelection

/**
 * Receiver of `architecture { }`.
 *
 * It holds groups and roles, exactly as a group block does — see [DeclarationContainerScope].
 * The root is a container like any other, so a small definition, or the first example anyone
 * reads, does not have to invent a group before it can name a role.
 *
 * ## Example 1: declare groups and roles inside architecture { }
 * ```kt
 * val arch = architecture {
 *     "Readme" { layout { "README.md".file() } }
 *     "domain".group {
 *         "UseCase" { }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface ArchitectureScope : DeclarationContainerScope {
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
    private val roles = mutableListOf<Role>()

    /**
     * One namespace for groups and roles alike, exactly as a group block keeps one.
     *
     * A top level group's `qualifiedName` is its bare name, and so is a root role's, so
     * `"domain".group { }` and `"domain" { }` would both answer to `"domain"` and nothing
     * could say which one a reference means. Sharing the reservations is how that is said.
     */
    private val declaredNames = DeclaredNames()

    override var files: FileSelection = FileSelection.GitTracked

    override var moduleResolver: ModuleResolver = ModuleResolver.Conventional

    override fun String.group(block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = this,
            parentPath = emptyList(),
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }

    override operator fun String.invoke(block: RoleScope.() -> Unit) {
        roles += declareRole(
            name = this,
            groupPath = emptyList(),
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }

    fun build(): Architecture = Architecture(
        groups = groups.toList(),
        roles = roles.toList(),
        files = files,
        moduleResolver = moduleResolver,
    )
}
