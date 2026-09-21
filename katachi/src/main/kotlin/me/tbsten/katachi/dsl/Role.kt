package me.tbsten.katachi.dsl

/**
 * One concrete example of a role, as `example("GetUserUseCase", "ユーザーを取得する")`.
 *
 * Name and description are separate arguments so that a description may contain anything,
 * `...` included.
 *
 * ## Example 1: attach one concrete example to a role
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             example("GetUserUseCase", "ユーザーを取得する")
 *         }
 *     }
 * }
 * arch.allRoles.single().examples shouldContainExactly
 *     listOf(RoleExample("GetUserUseCase", "ユーザーを取得する"))
 * ```
 */
public data class RoleExample(
    public val name: String,
    public val description: String,
)

/**
 * A role: what a file is for, and where it may live.
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
    /**
     * Display name. Defaults to [name].
     *
     * ## Example 1: separate the identifier from the display name
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { title = "ユースケース" }
     *     }
     * }
     * arch.allRoles.single().title shouldBe "ユースケース"
     * ```
     */
    public val title: String,
    /**
     * One paragraph describing the role.
     *
     * ## Example 1: set a summary
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { summary = "各画面で発生するアプリ固有の1つの振る舞い" }
     *     }
     * }
     * arch.allRoles.single().summary shouldBe "各画面で発生するアプリ固有の1つの振る舞い"
     * ```
     */
    public val summary: String?,
    /**
     * Examples, in the order `example()` was called.
     *
     * ## Example 1: read the examples added in declaration order
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             example("GetRecommendedProductListUseCase", "おすすめの商品リストを取得する")
     *             example("SignOutUseCase", "サインアウトする")
     *         }
     *     }
     * }
     * arch.allRoles.single().examples shouldContainExactly listOf(
     *     RoleExample("GetRecommendedProductListUseCase", "おすすめの商品リストを取得する"),
     *     RoleExample("SignOutUseCase", "サインアウトする"),
     * )
     * ```
     */
    public val examples: List<RoleExample>,
    /**
     * Whether this role is rendered into the generated documentation.
     *
     * The declared value is kept as written: it is not merged with the group's value.
     *
     * ## Example 1: keep a role out of the generated documentation
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { documented = false }
     *         "Repository" { }
     *     }
     * }
     * arch.allRoles.map { it.name to it.documented } shouldBe
     *     listOf("UseCase" to false, "Repository" to true)
     * ```
     */
    public val documented: Boolean,
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
     */
    public val qualifiedName: String = (groupPath + name).joinToString("/")

    override fun toString(): String = "Role($qualifiedName)"
}
