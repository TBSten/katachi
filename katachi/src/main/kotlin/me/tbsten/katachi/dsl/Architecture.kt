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
 *
 * ## Example 1: the value returned by architecture { }
 * ```kt
 * val projectArchitecture: Architecture = architecture {
 *     "domain".group {
 *         "UseCase" { }
 *     }
 * }
 * ```
 */
public class Architecture internal constructor(
    /**
     * Top level groups, in declaration order.
     *
     * ## Example 1: read the groups declared at the root
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { }
     *         "Repository" { }
     *     }
     * }
     * arch.groups.map { it.name } shouldContainExactly listOf("domain")
     * ```
     */
    public val groups: List<Group>,
    /**
     * Which files of the project the check looks at.
     *
     * ## Example 1: walk the whole tree instead of only git-tracked files
     * ```kt
     * val arch = architecture {
     *     files = wholeTree()
     *     "domain".group { "UseCase" { } }
     * }
     * arch.files shouldBe FileSelection.WholeTree
     * ```
     */
    public val files: FileSelection = FileSelection.GitTracked,
    /**
     * How a module path written in a `layout { }` becomes a directory.
     *
     * ## Example 1: replace the resolution rule for module paths
     * ```kt
     * val architecture = architecture {
     *     moduleResolver = ModuleResolver { module ->
     *         if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
     *     }
     *     "domain".group { "UseCase" { } }
     * }
     * architecture.moduleResolver.directoryOf(ModulePath.of(":app")) shouldBe "apps/android"
     * ```
     */
    public val moduleResolver: ModuleResolver = ModuleResolver.Conventional,
) {
    /**
     * Every group, parents before their children, in declaration order.
     *
     * ## Example 1: read nested groups together with their parents
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "model".group {
     *             "value".group {
     *                 "ValueObject" { }
     *             }
     *         }
     *     }
     * }
     * arch.allGroups.map { it.qualifiedName } shouldContainExactly
     *     listOf("domain", "domain/model", "domain/model/value")
     * ```
     */
    public val allGroups: List<Group> = groups.flatMap { it.selfAndDescendants() }

    /**
     * Every role of every group, in declaration order.
     *
     * ## Example 1: read every role across every group
     * ```kt
     * val arch = architecture {
     *     "domain".group { "Repository" { } }
     *     "data".group { "Repository" { } }
     * }
     * arch.allRoles.map { it.qualifiedName } shouldContainExactly
     *     listOf("domain/Repository", "data/Repository")
     * ```
     */
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
 * This function is deliberately not `inline`: the declaration sites are read off the
 * stack trace, and inlining remaps the line numbers past the end of the caller's file.
 *
 * ## Example 1: define an architecture with architecture { }
 * ```kt
 * val projectArchitecture = architecture {
 *     domainRoles()
 *     dataRoles()
 * }
 * ```
 *
 * ## Example 2: split the definition across files with an extension function
 * ```kt
 * // ArchitectureExtensions.kt
 * fun ArchitectureScope.domainRoles() {
 *     "domain".group {
 *         title = "ドメイン"
 *         "UseCase" {
 *             title = "ユースケース"
 *             summary = "各画面で発生するアプリ固有の1つの振る舞い"
 *         }
 *     }
 * }
 *
 * // Caller
 * val projectArchitecture = architecture {
 *     domainRoles()
 * }
 * ```
 */
public fun architecture(block: ArchitectureScope.() -> Unit): Architecture {
    val scope = ArchitectureScopeImpl()
    scope.block()
    return scope.build()
}
