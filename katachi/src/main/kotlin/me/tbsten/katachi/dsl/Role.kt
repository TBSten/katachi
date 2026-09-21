package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * A role: what a file is for, and where it may live.
 *
 * What a role *is* — its name, where it was declared, where its files live — is here. What
 * some processor wants to say *about* it is metadata, read with [get]: [Title], [Summary],
 * [Documented] and [Examples] are the ones katachi ships, and a processor adds its own with
 * [metadata]. Keeping them apart is what lets a new processor bring a new word without this
 * class growing a field for it.
 *
 * ## Example 1: declare a role and read it back
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             title = "ユースケース"
 *             summary = "各画面で発生するアプリ固有の1つの振る舞い"
 *         }
 *     }
 * }
 * arch.allRoles.single().name shouldBe "UseCase"
 * arch.allRoles.single()[Title] shouldBe "ユースケース"
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
     *     "domain".group { "UseCase" { title = "ユースケース" } }
     * }
     * arch.allRoles.single()[Title] shouldBe "ユースケース"
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun <T : Any> get(key: MetadataKey<T>): T? = metadata[key]

    override fun toString(): String = "Role($qualifiedName)"
}
