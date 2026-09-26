package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.FileConstraintDeclaration
import me.tbsten.katachi.dsl.internal.MetadataValues
import me.tbsten.katachi.dsl.internal.TemplateDeclaration
import me.tbsten.katachi.dsl.internal.get

/**
 * A role: what a file is for, and where it may live.
 *
 * What a role *is* — its name, where it was declared, where its files live — is here. What
 * some processor wants to say *about* it is metadata, read with [get]: [Title], [Summary],
 * [Description], [Documented] and [Examples] are the ones katachi ships, a processor adds its
 * own with [metadata], and whoever writes the definition adds a whole section of the generated
 * page with [documentSection]. Keeping them apart is what lets a new processor bring a new word
 * without this class growing a field for it.
 *
 * ## Example 1: declare a role and read it back
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             title = "Use case"
 *             summary = "A single app-specific behavior that happens on a screen"
 *         }
 *     }
 * }
 * arch.allRoles.single().name shouldBe "UseCase"
 * arch.allRoles.single()[Title] shouldBe "Use case"
 * ```
 */
public class Role internal constructor(
    /**
     * Identifier. Matches `[A-Za-z][A-Za-z0-9_-]*`.
     *
     * ## Example 1: read the declared role name
     * ```kt
     * val arch = architecture { "domain".group { "UseCase" { } } }
     * arch.allRoles.single().name shouldBe "UseCase"
     * ```
     */
    public val name: String,
    /** What was written on this role beyond its identity. Read through [get]. */
    internal val metadata: MetadataValues,
    /**
     * The `layout { }` blocks of this role, in declaration order. They have not been
     * evaluated. A role may declare several, one per place its files may live.
     *
     * ## Example 1: declare where a role's files may live
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             layout { }
     *         }
     *     }
     * }
     * arch.allRoles.single().layouts.size shouldBe 1
     * ```
     */
    public val layouts: List<LayoutDeclaration>,
    /**
     * Constraints written straight on this role, in declaration order. They have not been
     * evaluated, and they cover the union of every block in [layouts].
     *
     * Internal because a constraint says nothing until the layout around it has been
     * evaluated: what the check works with is [me.tbsten.katachi.dsl.internal.DeclaredFileConstraint], not this.
     */
    internal val fileConstraints: List<FileConstraintDeclaration>,
    /**
     * The `template { }` of this role, in declaration order. At most one is ever kept -- the
     * list is what makes the second one a refusal rather than a replacement.
     *
     * Internal for the reason [fileConstraints] is: a template says nothing until it has been
     * replayed with the values of one run.
     */
    internal val templates: List<TemplateDeclaration>,
    /**
     * Names of the groups this role sits in, outermost first.
     *
     * ## Example 1: read the group path of a nested role
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "model".group { "Entity" { } }
     *     }
     * }
     * arch.allRoles.single().groupPath shouldContainExactly listOf("domain", "model")
     * ```
     *
     * ## Example 2: a role declared at the root of architecture { } is in no group
     * ```kt
     * val arch = architecture { "Readme" { } }
     * arch.allRoles.single().groupPath shouldBe emptyList()
     * ```
     */
    public val groupPath: List<String>,
    /**
     * Where `"Name" { }` was written.
     *
     * ## Example 1: read where a role was declared
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             layout { }
     *         }
     *     }
     * }
     * arch.allRoles.single().declaredAt shouldBe DeclarationSite("DeclarationSiteSpec.kt", 17)
     * ```
     */
    public val declaredAt: DeclarationSite,
) {
    /**
     * [groupPath] and [name] joined with `/`, e.g. `domain/UseCase`.
     *
     * ## Example 1: read the qualified name of a nested role
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "model".group { "Entity" { } }
     *     }
     * }
     * arch.allRoles.single().qualifiedName shouldBe "domain/model/Entity"
     * ```
     *
     * ## Example 2: a role declared at the root is qualified by its name alone
     * ```kt
     * val arch = architecture { "Readme" { } }
     * arch.allRoles.single().qualifiedName shouldBe "Readme"
     * ```
     */
    public val qualifiedName: String = (groupPath + name).joinToString("/")

    /**
     * The value written under [key], or `null` when this role does not carry that key.
     *
     * The declared value, as written: nothing is inherited from the groups around it. A
     * processor that wants inheritance walks [groupPath] itself, where it can say what
     * combining two values means for its own word.
     *
     * ## Example 1: read a processor's own key off a role
     * ```kt
     * val Owner: MetadataKey<String> = metadata()
     * var RoleScope.owner: String? by Owner
     *
     * val arch = architecture {
     *     "domain".group { "UseCase" { owner = "platform" } }
     * }
     * arch.allRoles.single()[Owner] shouldBe "platform"
     * ```
     *
     * ## Example 2: read one of the keys katachi ships
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { title = "Use case" } }
     * }
     * arch.allRoles.single()[Title] shouldBe "Use case"
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun <T : Any> get(key: MetadataKey<T>): T? = metadata[key]

    /**
     * The Markdown written on this role under [section], or `null` when this role did not write
     * it.
     *
     * ## Example 1: read a section off a role
     * ```kt
     * val TestPolicy = documentSection("Testing")
     * var MetadataScope.testPolicy by TestPolicy
     *
     * val arch = architecture {
     *     "api".group { "Controller" { testPolicy = "- The status code" } }
     * }
     * arch.allRoles.single()[TestPolicy] shouldBe "- The status code"
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun get(section: DocumentSection): String? = metadata[section]

    override fun toString(): String = "Role($qualifiedName)"
}
