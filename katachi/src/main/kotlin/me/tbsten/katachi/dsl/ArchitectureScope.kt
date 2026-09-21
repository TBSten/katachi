package me.tbsten.katachi.dsl

import me.tbsten.katachi.check.FileSelection
import me.tbsten.katachi.check.ModuleResolver

/**
 * Anything that can hold groups: the root of the DSL and a group itself.
 *
 * ```kotlin
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
     * @param documented pass `false` to keep the group out of the generated
     *   documentation. It still takes part in the check.
     */
    public fun String.group(
        documented: Boolean = true,
        block: GroupScope.() -> Unit,
    )
}

/**
 * Receiver of `architecture { }`.
 *
 * It intentionally has no `String.invoke`: a role has to sit in a group, because a group
 * is what decides the role's documentation output directory.
 */
@KatachiDsl
public sealed interface ArchitectureScope : GroupContainerScope {
    /**
     * Which files of the project the check looks at. Defaults to [gitTracked].
     *
     * ```kotlin
     * val projectArchitecture = architecture {
     *   files = wholeTree()
     *   domainRoles()
     * }
     * ```
     */
    public var files: FileSelection

    /** Only the files git reports for this project. See [FileSelection.GitTracked]. */
    public fun gitTracked(): FileSelection = FileSelection.GitTracked

    /** Every file below the project root, whatever git thinks of it. See [FileSelection.WholeTree]. */
    public fun wholeTree(): FileSelection = FileSelection.WholeTree

    /**
     * How a module path written in a `layout { }` becomes a directory. Defaults to
     * [conventionalModuleResolver].
     *
     * ```kotlin
     * val projectArchitecture = architecture {
     *   moduleResolver = ModuleResolver { module ->
     *     if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
     *   }
     * }
     * ```
     */
    public var moduleResolver: ModuleResolver

    /** `:core:data` lives in `core/data`. See [ModuleResolver.Conventional]. */
    public fun conventionalModuleResolver(): ModuleResolver = ModuleResolver.Conventional
}

internal class ArchitectureScopeImpl : ArchitectureScope {
    private val groups = mutableListOf<Group>()
    private val declaredGroupNames = DeclaredNames()

    override var files: FileSelection = FileSelection.GitTracked

    override var moduleResolver: ModuleResolver = ModuleResolver.Conventional

    override fun String.group(documented: Boolean, block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = this,
            parentPath = emptyList(),
            documented = documented,
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredGroupNames,
            block = block,
        )
    }

    fun build(): Architecture =
        Architecture(groups = groups.toList(), files = files, moduleResolver = moduleResolver)
}
