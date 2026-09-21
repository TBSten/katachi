package me.tbsten.katachi.dsl

/**
 * Anything that can hold declarations: the root of `architecture { }`, and a group inside it.
 *
 * Both hold the same two things, groups and roles, so both say so through this one interface.
 * A group is one documentation output directory, and the root is simply the outermost one: a
 * role written straight into `architecture { }` sits in no group, its qualified name is its
 * own name, and there is one fewer concept to explain before the first definition can be read.
 *
 * ## Example 1: declare a role and a group side by side
 * ```kt
 * val arch = architecture {
 *     "Readme" { layout { "README.md".file() } }
 *     "domain".group {
 *         "UseCase" { }
 *     }
 * }
 * arch.allRoles.map { it.qualifiedName } shouldContainExactly listOf("Readme", "domain/UseCase")
 * ```
 *
 * ## Example 2: collect declarations in an ArchitectureScope extension function
 * ```kt
 * fun ArchitectureScope.domainRoles() {
 *   "domain".group { "UseCase" { } }
 * }
 * ```
 */
@KatachiDsl
public sealed interface DeclarationContainerScope {
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
     *         "UseCase" { }
     *     }
     * }
     * ```
     *
     * ## Example 2: keep a group out of the generated documentation
     * ```kt
     * val arch = architecture {
     *     "build".group {
     *         documented = false
     *         "VersionCatalog" { documented = false }
     *     }
     * }
     * ```
     */
    public fun String.group(block: GroupScope.() -> Unit)

    /**
     * Declares a role: `"UseCase" { title = "ユースケース" }`.
     *
     * This is `String.invoke`, so the role name is written as a plain string literal
     * followed by its block.
     *
     * A role declared directly in `architecture { }` carries an empty
     * [group path][Role.groupPath], so its [qualified name][Role.qualifiedName] is the role
     * name alone. Inside `architecture { }` a group and a role therefore cannot share a name:
     * both would answer to it, and a reference could not say which was meant.
     *
     * ## Example 1: declare a role inside a group
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             title = "ユースケース"
     *             summary = "各画面で発生するアプリ固有の1つの振る舞い"
     *         }
     *     }
     * }
     * ```
     *
     * ## Example 2: declare a role at the root, with no group around it
     * ```kt
     * val arch = architecture {
     *     "Readme" {
     *         title = "README"
     *         layout { "README.md".file() }
     *     }
     * }
     * arch.allRoles.single().qualifiedName shouldBe "Readme"
     * ```
     */
    public operator fun String.invoke(block: RoleScope.() -> Unit)
}
