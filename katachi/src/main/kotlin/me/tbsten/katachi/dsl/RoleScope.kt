package me.tbsten.katachi.dsl

/**
 * Receiver of `"RoleName" { }`.
 *
 * The five properties below are metadata under the hood — sugar over [Title], [Summary],
 * [Description], [Documented] and [Examples] — and a processor adds its own words the same
 * way, with `var RoleScope.owner by Owner`. They are members rather than extensions because
 * they are what a definition is mostly made of: an import per word would be a tax on the
 * common case.
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
public sealed interface RoleScope : MetadataScope, ConstraintScope {
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
     * Free-form Markdown about this role: what it is, what it may do, what it may not.
     *
     * Write [summary] for the one line that lands in a table cell, and this for everything
     * that needs more room. The text is kept exactly as written, newlines included, and
     * katachi adds no structure of its own: the headings inside are yours to choose.
     *
     * ## Example 1: give a role a body with sections of its own
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             summary = "各画面で発生するアプリ固有の1つの振る舞い"
     *             description = """
     *                 UI からは UseCase だけを呼び、Repository を直接触らない。
     *
     *                 ### やってはいけないこと
     *                 - Android の型に依存する
     *             """.trimIndent()
     *         }
     *     }
     * }
     * arch.allRoles.single()[Description].orEmpty().lines().size shouldBe 4
     * ```
     */
    public var description: String?

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

    /**
     * Constraints written straight on the role, which cover the union of every `layout { }`
     * it declares. See [ConstraintScope.constraint].
     */
    val constraints = mutableListOf<ConstraintDeclaration>()

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

    override var description: String?
        get() = metadata[Description]
        set(value) {
            metadata[Description] = value
        }

    override var documented: Boolean
        get() = metadata[Documented] ?: true
        set(value) {
            metadata[Documented] = value
        }

    // Accumulating, unlike the four properties above, so it reads the key back and appends.
    // The mechanism stays "one key, one value"; piling up is something this function does.
    override fun example(name: String, description: String) {
        metadata[Examples] = metadata[Examples].orEmpty() + RoleExample(name, description)
    }

    override fun layout(block: LayoutScope.() -> Unit) {
        layouts += LayoutDeclaration(declaredAt = captureDeclarationSite(), block = block)
    }

    override fun constraint(name: String?, declaredAt: DeclarationSite, check: FileSetConstraint) {
        constraints += constraintDeclarationOf(name = name, declaredAt = declaredAt, check = check)
    }
}

/**
 * Validates the name, takes it in [declaredNames], then evaluates [block] to collect the
 * role's properties.
 *
 * The name is reserved before [block] runs, for the same reason as in `declareGroup`.
 *
 * @throws KatachiConstraintWithoutLayoutException when the role wrote a constraint but no
 *   `layout { }`. It is decided here, at the end of the block, because that is the first
 *   moment both lists are complete — and it needs nothing else: no file is read, and no
 *   module index is consulted.
 */
internal fun declareRole(
    name: String,
    groupPath: List<String>,
    declaredAt: DeclarationSite,
    declaredNames: DeclaredNames,
    block: RoleScope.() -> Unit,
): Role {
    requireValidIdentifier(name, DeclarationKind.Role, declaredAt)
    requireNameIsFree(declaredNames, DeclarationKind.Role, name, groupPath, declaredAt)
    declaredNames.reserve(name, DeclarationKind.Role, declaredAt)
    val scope = RoleScopeImpl(name)
    scope.block()
    // A wildcard module key that currently matches nothing still counts as a layout: such a
    // role is a place waiting to fill up, which is the same thing `flattenLayout` already
    // decides when it leaves those declarations out of `required`.
    if (scope.constraints.isNotEmpty() && scope.layouts.isEmpty()) {
        throw KatachiConstraintWithoutLayoutException(
            role = name,
            declaredAt = scope.constraints.first().declaredAt,
        )
    }
    return Role(
        name = name,
        metadata = scope.metadata.build(),
        layouts = scope.layouts.toList(),
        constraints = scope.constraints.toList(),
        groupPath = groupPath,
        declaredAt = declaredAt,
    )
}
