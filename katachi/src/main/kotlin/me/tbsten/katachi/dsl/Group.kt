package me.tbsten.katachi.dsl

/**
 * A group of roles. One group is one documentation output directory; it says nothing
 * about where the files physically live, so the roles of one group may be spread over
 * several modules. Groups can nest.
 *
 * What a group *is* — its name, its path, what it holds — is here. What some processor
 * wants to say *about* it is metadata, read with [get]: [Title] and [Documented] are the
 * ones katachi ships for groups, and a processor adds its own with [metadata].
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
    /** What was written on this group beyond its identity. Read through [get]. */
    internal val metadata: MetadataValues,
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

    /**
     * The value written under [key], or `null` when this group does not carry that key.
     *
     * The declared value, as written: a nested group does not pick anything up from the one
     * around it, and neither do the roles inside it.
     *
     * ## Example 1: read a processor's own key off a group
     * ```kt
     * val Owner: MetadataKey<String> = metadata()
     * var GroupScope.owner: String? by Owner
     *
     * val arch = architecture {
     *     "domain".group { owner = "platform" }
     * }
     * arch.groups.single()[Owner] shouldBe "platform"
     * ```
     *
     * ## Example 2: read one of the keys katachi ships
     * ```kt
     * val arch = architecture {
     *     "build".group { documented = false }
     * }
     * arch.groups.single()[Documented] shouldBe false
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun <T : Any> get(key: MetadataKey<T>): T? = metadata[key]

    internal fun selfAndDescendants(): List<Group> =
        listOf(this) + groups.flatMap { it.selfAndDescendants() }

    override fun toString(): String = "Group($qualifiedName)"
}
