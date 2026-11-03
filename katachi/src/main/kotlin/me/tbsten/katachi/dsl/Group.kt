package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.MetadataValues
import me.tbsten.katachi.dsl.internal.TemplateSetDeclaration
import me.tbsten.katachi.dsl.internal.get

/**
 * A group of roles.
 *
 * One group is one documentation output directory; it says nothing
 * about where the files physically live, so the roles of one group may be spread over
 * several modules. Groups can nest.
 *
 * What a group *is* — its name, its path, what it holds — is here. What some processor wants to say
 * *about* it is metadata, read with [get]: [Title], [Summary], [Description] and [Documented] are
 * the ones katachi ships for groups, a processor adds its own with
 * [metadata()][me.tbsten.katachi.dsl.metadata], and whoever writes the definition adds a whole
 * section of the generated page with [documentSection].
 *
 * ## Example 1: declare nested groups and read them back
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         documented = false
 *         "model".group {
 *             "ValueObject".role { }
 *         }
 *         "UseCase".role { }
 *     }
 * }
 *
 * val domain = arch.groups.single()
 * domain.name shouldBe "domain"
 * domain.roles.map { it.name } shouldContainExactly listOf("UseCase")
 * domain.groups.single().path shouldContainExactly listOf("domain", "model")
 * domain.groups.single().qualifiedName shouldBe "domain.model"
 * domain[Documented] shouldBe false
 * println(domain.declaredAt) // where `"domain".group { }` was written
 * ```
 */
public class Group internal constructor(
    /**
     * Identifier. Matches `[A-Za-z][A-Za-z0-9_-]*`.
     */
    public val name: String,
    internal val metadata: MetadataValues,
    /**
     * Names from the outermost group down to this one.
     */
    public val path: List<String>,
    /**
     * Nested groups, in declaration order.
     */
    public val groups: List<Group>,
    /**
     * Roles declared directly in this group, in declaration order.
     */
    public val roles: List<Role>,
    /**
     * Where `"name".group { }` was written.
     */
    public val declaredAt: DeclarationSite,
    internal val templateSets: List<TemplateSetDeclaration> = emptyList(),
) {
    /**
     * [path][Group.path] joined with `.`, e.g. `domain.model`.
     */
    public val qualifiedName: String = path.joinToString(".")

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
     */
    @ExperimentalKatachiApi
    public operator fun <T : Any> get(key: MetadataKey<T>): T? = metadata[key]

    /**
     * The Markdown written on this group under [section], or `null` when this group did not
     * write it.
     *
     * ## Example 1: read a section off a group
     * ```kt
     * val TestPolicy = documentSection("Testing")
     * var DeclarationContainerScope.testPolicy by TestPolicy
     *
     * val arch = architecture {
     *     "api".group { testPolicy = "- The status code" }
     * }
     * arch.groups.single()[TestPolicy] shouldBe "- The status code"
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun get(section: DocumentSection): String? = metadata[section]

    internal fun selfAndDescendants(): List<Group> =
        listOf(this) + groups.flatMap { it.selfAndDescendants() }

    /** `Group(<qualifiedName>)`. */
    override fun toString(): String = "Group($qualifiedName)"
}
