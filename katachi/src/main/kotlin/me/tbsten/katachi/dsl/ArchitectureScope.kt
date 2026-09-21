package me.tbsten.katachi.dsl

import me.tbsten.katachi.check.FileSelection

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
}

internal class ArchitectureScopeImpl : ArchitectureScope {
    private val groups = mutableListOf<Group>()
    private val declaredGroupNames = DeclaredNames()

    override var files: FileSelection = FileSelection.GitTracked

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

    fun build(): Architecture = Architecture(groups = groups.toList(), files = files)
}
