package me.tbsten.katachi.dsl

/**
 * A group of roles. One group is one documentation output directory; it says nothing
 * about where the files physically live, so the roles of one group may be spread over
 * several modules. Groups can nest.
 *
 * ## Example 1: declare nested groups
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "model".group {
 *             "ValueObject" { }
 *         }
 *         "UseCase" { }
 *     }
 * }
 * ```
 */
public class Group internal constructor(
    /**
     * Identifier. Matches `[A-Za-z][A-Za-z0-9_-]*`.
     *
     * ## Example 1: read the declared group name
     * ```kt
     * val arch = architecture { "domain".group { } }
     * arch.groups.single().name shouldBe "domain"
     * ```
     */
    public val name: String,
    /**
     * Display name. Defaults to [name].
     *
     * ## Example 1: separate the identifier from the display name
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         title = "ドメイン"
     *         "UseCase" { title = "ユースケース" }
     *     }
     * }
     * arch.groups.single().title shouldBe "ドメイン"
     * ```
     */
    public val title: String,
    /**
     * Whether this group is rendered into the generated documentation.
     *
     * The declared value is kept as written: it is not merged with the parent's value.
     *
     * ## Example 1: keep a group out of the generated documentation
     * ```kt
     * val arch = architecture {
     *     "Gradle".group(documented = false) {
     *         "VersionCatalog" { }
     *     }
     * }
     * arch.groups.single().documented shouldBe false
     * ```
     */
    public val documented: Boolean,
    /**
     * Names from the outermost group down to this one.
     *
     * ## Example 1: read the path of a nested group
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "model".group { }
     *     }
     * }
     * val model = arch.groups.single().groups.single()
     * model.path shouldContainExactly listOf("domain", "model")
     * ```
     */
    public val path: List<String>,
    /**
     * Nested groups, in declaration order.
     *
     * ## Example 1: read the groups declared directly inside this one
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "model".group { }
     *         "UseCase" { }
     *     }
     * }
     * arch.groups.single().groups.map { it.name } shouldContainExactly listOf("model")
     * ```
     */
    public val groups: List<Group>,
    /**
     * Roles declared directly in this group, in declaration order.
     *
     * ## Example 1: read the roles declared directly inside this group
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "model".group { "ValueObject" { } }
     *         "UseCase" { }
     *     }
     * }
     * arch.groups.single().roles.map { it.name } shouldContainExactly listOf("UseCase")
     * ```
     */
    public val roles: List<Role>,
    /**
     * Where `"name".group { }` was written.
     *
     * ## Example 1: read where a group was declared
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             layout { }
     *         }
     *     }
     * }
     * arch.allGroups.single().declaredAt shouldBe DeclarationSite("DeclarationSiteSpec.kt", 16)
     * ```
     */
    public val declaredAt: DeclarationSite,
) {
    /**
     * [path] joined with `/`, e.g. `domain/model`.
     *
     * ## Example 1: read the qualified name of a nested group
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "model".group { }
     *     }
     * }
     * arch.groups.single().groups.single().qualifiedName shouldBe "domain/model"
     * ```
     */
    public val qualifiedName: String = path.joinToString("/")

    internal fun selfAndDescendants(): List<Group> =
        listOf(this) + groups.flatMap { it.selfAndDescendants() }

    override fun toString(): String = "Group($qualifiedName)"
}
