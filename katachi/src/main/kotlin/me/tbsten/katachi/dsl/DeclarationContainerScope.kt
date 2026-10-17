package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Anything that can hold declarations: the root of `architecture { }`, and a group inside it.
 *
 * Both hold the same two things, groups and roles, so both say so through this one interface.
 * A group is one documentation output directory, and the root is simply the outermost one: a
 * role written straight into `architecture { }` sits in no group, its qualified name is its
 * own name, and there is one fewer concept to explain before the first definition can be read.
 *
 * It is a [MetadataScope] as well, so a word that belongs to a container rather than to a role
 * is offered once — `var DeclarationContainerScope.testPolicy by TestPolicy` — and can then be
 * written on a group and on the root alike.
 *
 * ## Example 1: declare a role and a group side by side
 * ```kt
 * val arch = architecture {
 *     "Readme".role { layout { "README.md".file() } }
 *     "domain".group {
 *         "UseCase".role { }
 *     }
 * }
 * arch.allRoles.map { it.qualifiedName } shouldContainExactly listOf("Readme", "domain.UseCase")
 * ```
 *
 * ## Example 2: collect declarations in an ArchitectureScope extension function
 * ```kt
 * fun ArchitectureScope.domainRoles() {
 *   "domain".group { "UseCase".role { } }
 * }
 * ```
 */
@KatachiDsl
public sealed interface DeclarationContainerScope : MetadataScope {
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
     *         "UseCase".role { }
     *     }
     * }
     * ```
     *
     * ## Example 2: keep a group out of the generated documentation
     * ```kt
     * val arch = architecture {
     *     "build".group {
     *         documented = false
     *         "VersionCatalog".role { documented = false }
     *     }
     * }
     * ```
     *
     * @featured
     */
    public fun String.group(block: GroupScope.() -> Unit)

    /**
     * Declares a role: `"UseCase".role { title = "Use case" }`.
     *
     * The role name is written as a plain string literal, then `.role` and its block. The
     * shape matches `.group { }` next to it, and it tells a role apart from a directory
     * declaration inside `layout { }`, where `"src" { }` is a directory and not a role.
     *
     * A role declared directly in `architecture { }` carries an empty
     * [group path][Role.groupPath], so its [qualified name][Role.qualifiedName] is the role
     * name alone. A group and a role declared in the same container cannot share a name:
     * both would answer to the same qualified name, and a reference could not say which was
     * meant.
     *
     * ## Example 1: declare a role inside a group
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase".role {
     *             title = "Use case"
     *             summary = "A single app-specific behavior that happens on a screen"
     *         }
     *     }
     * }
     * ```
     *
     * ## Example 2: declare a role at the root, with no group around it
     * ```kt
     * val arch = architecture {
     *     "Readme".role {
     *         title = "README"
     *         layout { "README.md".file() }
     *     }
     * }
     * arch.allRoles.single().qualifiedName shouldBe "Readme"
     * ```
     *
     * @featured
     */
    public fun String.role(block: RoleScope.() -> Unit)

    /**
     * Declares a template set: several templates gathered under one name, generated together by
     * one `--arg template=<name>`. See [TemplateSetScope].
     *
     * The name shares one namespace with the groups and roles declared beside it, and is written
     * in full as a role's is: `feature.NewFeature` inside the group `feature`, `NewFeature` at the
     * root.
     *
     * ## Example 1: declare a template set beside the roles it gathers
     * ```kt
     * val arch = architecture {
     *     "feature".group {
     *         "FeatureScreen".role {
     *             layout { "screen" / "FeatureScreen".ktFile().template { "// screen" } }
     *         }
     *         "NewFeature".templateSet {
     *             template("feature.FeatureScreen")
     *         }
     *     }
     * }
     * ```
     *
     * @see TemplateSetScope
     */
    @ExperimentalKatachiApi
    public fun String.templateSet(block: TemplateSetScope.() -> Unit)

    /**
     * The former spelling of a role declaration, `"UseCase" { }`. Write `"UseCase".role { }`.
     *
     * Only the role declaration changed: a directory inside `layout { }` is still written
     * `"src" { }`.
     *
     * ## Example 1: rewrite the old spelling
     * ```kt
     * // Before (v0.2): "UseCase" { title = "Use case" }
     * val arch = architecture {
     *     "UseCase".role { title = "Use case" }
     * }
     * ```
     */
    @Deprecated(
        message = "\"UseCase\" { } is no longer a role declaration. " +
            "Write \"UseCase\".role { } instead. " +
            "A directory inside layout { } is still written \"src\" { }.",
        replaceWith = ReplaceWith("role(block)"),
        level = DeprecationLevel.ERROR,
    )
    public operator fun String.invoke(block: RoleScope.() -> Unit) {
        role(block)
    }
}
