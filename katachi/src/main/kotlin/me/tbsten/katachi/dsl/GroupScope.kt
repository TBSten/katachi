package me.tbsten.katachi.dsl

/**
 * Receiver of `"name".group { }`. Holds nested groups and roles.
 *
 * ## Example 1: declare nested groups and roles
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         title = "ドメイン"
 *         "UseCase" { }
 *         "model".group { }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface GroupScope : GroupContainerScope {
    /**
     * Display name of this group. Defaults to the group name.
     *
     * ## Example 1: separate the identifier from the display name
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         title = "ドメイン"
     *     }
     * }
     * arch.groups.single().title shouldBe "ドメイン"
     * ```
     */
    public var title: String

    /**
     * Declares a role: `"UseCase" { title = "ユースケース" }`.
     *
     * This is `String.invoke`, so the role name is written as a plain string literal
     * followed by its block.
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
     */
    public operator fun String.invoke(block: RoleScope.() -> Unit)
}

internal class GroupScopeImpl(private val path: List<String>) : GroupScope {
    override var title: String = path.last()

    val groups = mutableListOf<Group>()
    val roles = mutableListOf<Role>()

    private val declaredGroupNames = DeclaredNames()
    private val declaredRoleNames = DeclaredNames()

    override fun String.group(documented: Boolean, block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = this,
            parentPath = path,
            documented = documented,
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredGroupNames,
            block = block,
        )
    }

    override operator fun String.invoke(block: RoleScope.() -> Unit) {
        roles += declareRole(
            name = this,
            groupPath = path,
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredRoleNames,
            block = block,
        )
    }
}

/**
 * Validates the name, takes it in [declaredNames], then evaluates [block] to collect the
 * nested groups and roles.
 *
 * The name is reserved before [block] runs so that a block which reaches back into this
 * same scope cannot slip a second declaration of the same name past the check.
 */
internal fun declareGroup(
    name: String,
    parentPath: List<String>,
    documented: Boolean,
    declaredAt: DeclarationSite,
    declaredNames: DeclaredNames,
    block: GroupScope.() -> Unit,
): Group {
    requireValidIdentifier(name, DeclarationKind.Group, declaredAt)
    requireNoDuplicateGroup(declaredNames, name, parentPath, declaredAt)
    declaredNames.reserve(name, declaredAt)
    val path = parentPath + name
    val scope = GroupScopeImpl(path)
    scope.block()
    return Group(
        name = name,
        title = scope.title,
        documented = documented,
        path = path,
        groups = scope.groups.toList(),
        roles = scope.roles.toList(),
        declaredAt = declaredAt,
    )
}
