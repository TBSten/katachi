package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Anything that can hold declarations: the root of `architecture { }`, and a group inside it.
 *
 * Both hold groups and roles. A role written straight into `architecture { }` sits in no group,
 * so its qualified name is its own name. Groups nest, and a group and a role declared in the
 * same container cannot share a name.
 *
 * It is a [MetadataScope] as well, so a property declared once on this type
 * (`var DeclarationContainerScope.testPolicy by TestPolicy`) can be written on a group and on the
 * root alike.
 *
 * ## Example 1: declare a role and a group side by side
 * ```kt
 * val arch = architecture {
 *     "Readme".role { layout { "README.md".file() } }
 *     "domain".group {
 *         "UseCase".role {
 *             title = "Use case"
 *             summary = "A single app-specific behavior that happens on a screen"
 *         }
 *         "model".group { "ValueObject".role { } }
 *     }
 * }
 * arch.allRoles.map { it.qualifiedName } shouldContainExactly
 *     listOf("Readme", "domain.UseCase", "domain.model.ValueObject")
 * ```
 *
 * ## Example 2: collect declarations in an ArchitectureScope extension function
 * ```kt
 * fun ArchitectureScope.domainRoles() {
 *   "domain".group { "UseCase".role { } }
 * }
 * ```
 *
 * ## Example 3: gather templates into a template set
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
 */
@KatachiDsl
public sealed interface DeclarationContainerScope : MetadataScope {
    /**
     * Declares a group. Nest `group` calls to nest groups.
     *
     * Everything a group carries, metadata included, is written inside the block.
     *
     * @featured
     */
    public fun String.group(block: GroupScope.() -> Unit)

    /**
     * Declares a role: `"UseCase".role { title = "Use case" }`.
     *
     * A role declared directly in `architecture { }` carries an empty
     * [group path][Role.groupPath], so its [qualified name][Role.qualifiedName] is the role
     * name alone. A group and a role declared in the same container cannot share a name.
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
     * @see TemplateSetScope
     */
    @ExperimentalKatachiApi
    public fun String.templateSet(block: TemplateSetScope.() -> Unit)

    /**
     * The former spelling of a role declaration, `"UseCase" { }`. Write `"UseCase".role { }`.
     *
     * Only the role declaration changed: a directory inside `layout { }` is still written
     * `"src" { }`.
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
