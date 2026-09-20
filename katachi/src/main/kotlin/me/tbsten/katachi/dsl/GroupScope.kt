package me.tbsten.katachi.dsl

/** Receiver of `"name".group { }`. Holds nested groups and roles. */
@KatachiDsl
public sealed interface GroupScope : GroupContainerScope {
    /** Display name of this group. Defaults to the group name. */
    public var title: String

    /**
     * Declares a role: `"UseCase" { title = "ユースケース" }`.
     *
     * This is `String.invoke`, so the role name is written as a plain string literal
     * followed by its block.
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
    requireValidIdentifier(name, IdentifierKind.Group, declaredAt)
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
