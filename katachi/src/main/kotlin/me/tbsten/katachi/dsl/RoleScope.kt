package me.tbsten.katachi.dsl

/**
 * Receiver of `"RoleName" { }`.
 *
 * ## Example 1: set a role's properties
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             title = "ユースケース"
 *             summary = "各画面で発生するアプリ固有の1つの振る舞い"
 *             example("GetUserUseCase", "ユーザーを取得する")
 *         }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface RoleScope {
    /**
     * Display name. Defaults to the role name.
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
    public var title: String

    /**
     * One paragraph describing what this role is for.
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
    public var summary: String?

    /**
     * Set to `false` to keep this role out of the generated documentation. It still takes
     * part in the check.
     *
     * ## Example 1: keep a role out of the generated documentation
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { documented = false }
     *     }
     * }
     * arch.allRoles.single().documented shouldBe false
     * ```
     */
    public var documented: Boolean

    /**
     * Adds a concrete example. Call it once per example; the examples are kept in the
     * order they were added.
     *
     * ## Example 1: add a concrete example
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             example("GetUserUseCase", "ユーザーを取得する")
     *         }
     *     }
     * }
     * ```
     */
    public fun example(name: String, description: String)

    /**
     * Declares where files of this role may live. Call it once per place.
     *
     * The block is stored, not evaluated: see [LayoutDeclaration].
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
     * ```
     */
    public fun layout(block: LayoutScope.() -> Unit)
}

internal class RoleScopeImpl(name: String) : RoleScope {
    override var title: String = name
    override var summary: String? = null
    override var documented: Boolean = true

    val examples = mutableListOf<RoleExample>()
    val layouts = mutableListOf<LayoutDeclaration>()

    override fun example(name: String, description: String) {
        examples += RoleExample(name, description)
    }

    override fun layout(block: LayoutScope.() -> Unit) {
        layouts += LayoutDeclaration(declaredAt = captureDeclarationSite(), block = block)
    }
}

/**
 * Validates the name, takes it in [declaredNames], then evaluates [block] to collect the
 * role's properties.
 *
 * The name is reserved before [block] runs, for the same reason as in `declareGroup`.
 */
internal fun declareRole(
    name: String,
    groupPath: List<String>,
    declaredAt: DeclarationSite,
    declaredNames: DeclaredNames,
    block: RoleScope.() -> Unit,
): Role {
    requireValidIdentifier(name, DeclarationKind.Role, declaredAt)
    requireNoDuplicateRole(declaredNames, name, groupPath, declaredAt)
    declaredNames.reserve(name, declaredAt)
    val scope = RoleScopeImpl(name)
    scope.block()
    return Role(
        name = name,
        title = scope.title,
        summary = scope.summary,
        examples = scope.examples.toList(),
        documented = scope.documented,
        layouts = scope.layouts.toList(),
        groupPath = groupPath,
        declaredAt = declaredAt,
    )
}
