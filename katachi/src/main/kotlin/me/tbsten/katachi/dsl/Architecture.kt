package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.MetadataValues
import me.tbsten.katachi.dsl.internal.TemplateSetDeclaration
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.get

/**
 * The whole architecture definition: the groups and roles declared in `architecture { }`.
 *
 * Building this value runs no check and reads nothing from the file system. Hold it in a top
 * level `val` and read it from tests and from documentation generation alike.
 *
 * ## Example 1: the value returned by architecture { }
 * ```kt
 * val projectArchitecture: Architecture = architecture {
 *     files = wholeTree()
 *     "Readme".role { }
 *     "domain".group {
 *         "UseCase".role { }
 *         "model".group { "ValueObject".role { } }
 *     }
 * }
 *
 * projectArchitecture.groups.map { it.name } shouldBe listOf("domain")
 * projectArchitecture.roles.map { it.qualifiedName } shouldBe listOf("Readme")
 * projectArchitecture.allGroups.map { it.qualifiedName } shouldBe listOf("domain", "domain.model")
 * projectArchitecture.allRoles.map { it.qualifiedName } shouldBe
 *     listOf("Readme", "domain.UseCase", "domain.model.ValueObject")
 * projectArchitecture.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
 * ```
 */
public class Architecture internal constructor(
    /** Top level groups, in declaration order. */
    public val groups: List<Group>,
    /**
     * Roles declared straight into `architecture { }`, outside any group, in declaration
     * order.
     *
     * Their [group path][Role.groupPath] is empty, so their qualified name is the role name
     * alone. The root is a container like a group, and this is what it holds of its own.
     */
    public val roles: List<Role>,
    /** Which files of the project the check looks at. */
    public val files: FileSelection,
    /**
     * How a module path katachi finds becomes a directory: always [ModuleResolver.Conventional].
     *
     * `architecture { }` stopped taking a resolver when `.module { }` was removed; the value
     * stays here for the code that looks up each module's directory.
     */
    public val moduleResolver: ModuleResolver,
    internal val metadata: MetadataValues,
    /**
     * The ledger of violations `assert()` holds back, or `null` when nothing is held back. See
     * [Baseline].
     */
    @ExperimentalKatachiApi
    public val baseline: Baseline? = null,
    /**
     * Where `architecture { }` itself was called, as `FileName.kt:42`.
     *
     * A report points here when a violation has neither a file nor a declaration site of its
     * own. [DeclarationSite.Unknown] when the call could not be found on the stack.
     */
    @ExperimentalKatachiApi
    public val declaredAt: DeclarationSite = DeclarationSite.Unknown,
    internal val templateSets: List<TemplateSetDeclaration> = emptyList(),
) {
    /** Every group, parents before their children, in declaration order. */
    public val allGroups: List<Group> = groups.flatMap { it.selfAndDescendants() }

    /**
     * Every role: the root's own first, then those of every group, in declaration order.
     *
     * A container's own roles come before the roles of the containers inside it, which is the
     * order [allGroups] already puts groups in.
     */
    public val allRoles: List<Role> = roles + allGroups.flatMap { it.roles }

    internal val allTemplateSets: List<TemplateSetDeclaration> =
        templateSets + allGroups.flatMap { it.templateSets }

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
     *     "domain".group { "UseCase".role { } }
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

    /** The top-level group names and the number of roles, not the whole definition. */
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
 *         "UseCase".role {
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
 * @throws me.tbsten.katachi.KatachiDeclarationException when a declaration in the block is
 *   invalid, such as a duplicated or malformed name.
 * @featured
 */
public fun architecture(block: ArchitectureScope.() -> Unit): Architecture {
    val declaredAt = captureDeclarationSite()
    val scope = ArchitectureScopeImpl()
    scope.block()
    return scope.build(declaredAt)
}
