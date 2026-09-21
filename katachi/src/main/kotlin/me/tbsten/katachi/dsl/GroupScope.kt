package me.tbsten.katachi.dsl

/**
 * Receiver of `"name".group { }`. Holds nested groups and roles.
 *
 * [title] and [documented] are sugar over the [Title] and [Documented] metadata keys, the
 * same way the role block's properties are.
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
public sealed interface GroupScope : DeclarationContainerScope, MetadataScope {
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
     * arch.groups.single()[Title] shouldBe "ドメイン"
     * ```
     */
    public var title: String

    /**
     * Set to `false` to keep this group out of the generated documentation. It still takes
     * part in the check.
     *
     * The value is not inherited: a role inside a group that opted out has to say so for
     * itself, and so does a nested group.
     *
     * ## Example 1: keep a group out of the generated documentation
     * ```kt
     * val arch = architecture {
     *     "build".group {
     *         documented = false
     *         "VersionCatalog" { documented = false }
     *     }
     * }
     * arch.groups.single()[Documented] shouldBe false
     * ```
     */
    public var documented: Boolean
}

internal class GroupScopeImpl(private val path: List<String>) : GroupScope {
    val metadata = MetadataBuilder()

    val groups = mutableListOf<Group>()
    val roles = mutableListOf<Role>()

    // One namespace for groups and roles alike, the same as the root of `architecture { }`.
    // `"x".group { "domain".group { }; "domain" { } }` gives both the qualified name
    // "x/domain", so a reference to it could not say which one it means.
    private val declaredNames = DeclaredNames()

    override var title: String
        get() = metadata[Title] ?: path.last()
        set(value) {
            metadata[Title] = value
        }

    override var documented: Boolean
        get() = metadata[Documented] ?: true
        set(value) {
            metadata[Documented] = value
        }

    override fun String.group(block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = this,
            parentPath = path,
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }

    override operator fun String.invoke(block: RoleScope.() -> Unit) {
        roles += declareRole(
            name = this,
            groupPath = path,
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }
}

/**
 * Validates the name, takes it in [declaredNames], then evaluates [block] to collect the
 * nested groups, the roles and the metadata.
 *
 * The name is reserved before [block] runs so that a block which reaches back into this
 * same scope cannot slip a second declaration of the same name past the check.
 */
internal fun declareGroup(
    name: String,
    parentPath: List<String>,
    declaredAt: DeclarationSite,
    declaredNames: DeclaredNames,
    block: GroupScope.() -> Unit,
): Group {
    requireValidIdentifier(name, DeclarationKind.Group, declaredAt)
    requireNameIsFree(declaredNames, DeclarationKind.Group, name, parentPath, declaredAt)
    declaredNames.reserve(name, DeclarationKind.Group, declaredAt)
    val path = parentPath + name
    val scope = GroupScopeImpl(path)
    scope.block()
    return Group(
        name = name,
        metadata = scope.metadata.build(),
        path = path,
        groups = scope.groups.toList(),
        roles = scope.roles.toList(),
        declaredAt = declaredAt,
    )
}
