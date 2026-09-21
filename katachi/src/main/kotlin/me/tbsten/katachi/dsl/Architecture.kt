package me.tbsten.katachi.dsl

import me.tbsten.katachi.check.FileSelection
import me.tbsten.katachi.check.ModuleResolver

/**
 * The whole architecture definition: the groups declared in `architecture { }` and,
 * through them, every role.
 *
 * Building this value runs no check and reads nothing from the file system. The same
 * value is meant to be held in a top level `val` and read both by tests and (from v0.3)
 * by documentation generation.
 */
public class Architecture internal constructor(
    /** Top level groups, in declaration order. */
    public val groups: List<Group>,
    /** Which files of the project the check looks at. */
    public val files: FileSelection = FileSelection.GitTracked,
    /** How a module path written in a `layout { }` becomes a directory. */
    public val moduleResolver: ModuleResolver = ModuleResolver.Conventional,
) {
    /** Every group, parents before their children, in declaration order. */
    public val allGroups: List<Group> = groups.flatMap { it.selfAndDescendants() }

    /** Every role of every group, in declaration order. */
    public val allRoles: List<Role> = allGroups.flatMap { it.roles }

    override fun toString(): String =
        "Architecture(groups=${groups.map { it.name }}, roles=${allRoles.size})"
}

/**
 * Entry point of the DSL. Declaring a group inside the block registers it; there is no
 * separate "add it to a list" step.
 *
 * Split a large definition across files with extension functions on [ArchitectureScope]
 * and call them from the block. Note that an extension function cannot be called by its
 * fully qualified name, so the split files have to be imported.
 *
 * ```kotlin
 * val projectArchitecture = architecture {
 *   domainRoles()
 *   dataRoles()
 * }
 * ```
 *
 * This function is deliberately not `inline`: the declaration sites are read off the
 * stack trace, and inlining remaps the line numbers past the end of the caller's file.
 */
public fun architecture(block: ArchitectureScope.() -> Unit): Architecture {
    val scope = ArchitectureScopeImpl()
    scope.block()
    return scope.build()
}
