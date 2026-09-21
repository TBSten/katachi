package me.tbsten.katachi.dsl

/**
 * Receiver of `"RoleName" { }`.
 *
 * The four properties below are metadata under the hood — sugar over [Title], [Summary],
 * [Documented] and [Examples] — and a processor adds its own words the same way, with
 * `var RoleScope.owner by Owner`. They are members rather than extensions because they are
 * what a definition is mostly made of: an import per word would be a tax on the common case.
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
public sealed interface RoleScope : MetadataScope {
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
     * arch.allRoles.single()[Title] shouldBe "ユースケース"
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
     * arch.allRoles.single()[Summary] shouldBe "各画面で発生するアプリ固有の1つの振る舞い"
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
     * arch.allRoles.single()[Documented] shouldBe false
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

internal class RoleScopeImpl(private val roleName: String) : RoleScope {
    val metadata = MetadataBuilder()
    val layouts = mutableListOf<LayoutDeclaration>()

    override var title: String
        // The fallback lives here and is not written into the metadata: `Title` is absent
        // unless someone wrote it, which is what lets a reader tell "called it that on
        // purpose" from "never said".
        get() = metadata[Title] ?: roleName
        set(value) {
            metadata[Title] = value
        }

    override var summary: String?
        get() = metadata[Summary]
        set(value) {
            metadata[Summary] = value
        }

    override var documented: Boolean
        get() = metadata[Documented] ?: true
        set(value) {
            metadata[Documented] = value
        }

    // Accumulating, unlike the three properties above, so it reads the key back and appends.
    // The mechanism stays "one key, one value"; piling up is something this function does.
    override fun example(name: String, description: String) {
        metadata[Examples] = metadata[Examples].orEmpty() + RoleExample(name, description)
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
        metadata = scope.metadata.build(),
        layouts = scope.layouts.toList(),
        groupPath = groupPath,
        declaredAt = declaredAt,
    )
}
