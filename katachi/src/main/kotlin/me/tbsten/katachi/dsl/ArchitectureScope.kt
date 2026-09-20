package me.tbsten.katachi.dsl

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
public sealed interface ArchitectureScope : GroupContainerScope

internal class ArchitectureScopeImpl : ArchitectureScope {
    private val groups = mutableListOf<Group>()

    override fun String.group(documented: Boolean, block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = this,
            parentPath = emptyList(),
            documented = documented,
            declaredAt = captureDeclarationSite(),
            siblings = groups,
            block = block,
        )
    }

    fun build(): Architecture = Architecture(groups.toList())
}
