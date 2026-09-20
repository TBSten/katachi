package me.tbsten.katachi.dsl

/** Receiver of `"RoleName" { }`. */
@KatachiDsl
public sealed interface RoleScope {
    /** Display name. Defaults to the role name. */
    public var title: String

    /** One paragraph describing what this role is for. */
    public var summary: String?

    /**
     * Set to `false` to keep this role out of the generated documentation. It still takes
     * part in the check.
     */
    public var documented: Boolean

    /**
     * Adds a concrete example. Call it once per example; the examples are kept in the
     * order they were added.
     */
    public fun example(name: String, description: String)

    /**
     * Declares where files of this role may live. Call it once per place.
     *
     * The block is stored, not evaluated: see [LayoutDeclaration].
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
 * Validates the name, rejects a duplicate among [siblings], then evaluates [block] to
 * collect the role's properties.
 */
internal fun declareRole(
    name: String,
    groupPath: List<String>,
    declaredAt: DeclarationSite,
    siblings: List<Role>,
    block: RoleScope.() -> Unit,
): Role {
    requireValidIdentifier(name, IdentifierKind.Role, declaredAt)
    requireNoDuplicateRole(siblings, name, groupPath, declaredAt)
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
