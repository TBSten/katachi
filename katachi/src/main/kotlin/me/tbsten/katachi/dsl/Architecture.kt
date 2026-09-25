package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.fs.FileSelection

/**
 * The whole architecture definition: what was declared in `architecture { }` — the groups,
 * the roles written straight into the root, and through the groups every other role.
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
     * Roles declared straight into `architecture { }`, outside any group, in declaration
     * order.
     *
     * Their [group path][Role.groupPath] is empty, so their qualified name is the role name
     * alone. The root is a container like a group, and this is what it holds of its own.
     *
     * ## Example 1: read the roles declared at the root
     * ```kt
     * val arch = architecture {
     *     "Readme" { }
     *     "domain".group { "UseCase" { } }
     * }
     * arch.roles.map { it.qualifiedName } shouldContainExactly listOf("Readme")
     * ```
     */
    public val roles: List<Role>,
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
    public val files: FileSelection,
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
    public val moduleResolver: ModuleResolver,
    /**
     * What was written on `architecture { }` itself, beyond the declarations. Read through [get].
     */
    internal val metadata: MetadataValues,
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
     * Every role: the root's own first, then those of every group, in declaration order.
     *
     * A container's own roles come before the roles of the containers inside it, which is the
     * order [allGroups] already puts groups in.
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
     *
     * ## Example 2: a role declared at the root is one of them
     * ```kt
     * val arch = architecture {
     *     "Readme" { }
     *     "domain".group { "UseCase" { } }
     * }
     * arch.allRoles.map { it.qualifiedName } shouldContainExactly
     *     listOf("Readme", "domain/UseCase")
     * ```
     */
    public val allRoles: List<Role> = roles + allGroups.flatMap { it.roles }

    /**
     * The value written on the root under [key], or `null` when nothing was written there.
     *
     * The root is a container like a group, so something said about the whole definition is
     * written and read the same way a group's is — see [Group.get].
     *
     * ## Example 1: read a processor's own key off the root
     * ```kt
     * val Owner: MetadataKey<String> = metadata()
     * var DeclarationContainerScope.owner: String? by Owner
     *
     * val arch = architecture {
     *     owner = "platform"
     *     "domain".group { "UseCase" { } }
     * }
     * arch[Owner] shouldBe "platform"
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun <T : Any> get(key: MetadataKey<T>): T? = metadata[key]

    /**
     * The Markdown written on the root under [section], or `null` when that section was left
     * unwritten there.
     *
     * ## Example 1: read a section off the root
     * ```kt
     * val TestPolicy = documentSection("Testing")
     * var DeclarationContainerScope.testPolicy by TestPolicy
     *
     * val arch = architecture {
     *     testPolicy = "- Every module is checked by `assert()`"
     * }
     * arch[TestPolicy] shouldBe "- Every module is checked by `assert()`"
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun get(section: DocumentSection): String? = metadata[section]

    override fun toString(): String =
        "Architecture(groups=${groups.map { it.name }}, roles=${allRoles.size})"
}

/**
 * Entry point of the DSL. Declaring a group or a role inside the block registers it; there
 * is no separate "add it to a list" step.
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
 *         title = "Domain"
 *         "UseCase" {
 *             title = "Use case"
 *             summary = "A single app-specific behavior that happens on a screen"
 *         }
 *     }
 * }
 *
 * // Caller
 * val projectArchitecture = architecture {
 *     domainRoles()
 * }
 * ```
 *
 * @featured
 */
public fun architecture(block: ArchitectureScope.() -> Unit): Architecture {
    val scope = ArchitectureScopeImpl()
    scope.block()
    return scope.build()
}
