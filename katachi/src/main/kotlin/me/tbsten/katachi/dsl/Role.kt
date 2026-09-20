package me.tbsten.katachi.dsl

/**
 * One concrete example of a role, as `example("GetUserUseCase", "ユーザーを取得する")`.
 *
 * Name and description are separate arguments so that a description may contain anything,
 * `...` included.
 */
public data class RoleExample(
    public val name: String,
    public val description: String,
)

/** A role: what a file is for, and where it may live. */
public class Role internal constructor(
    /** Identifier. Matches `[A-Za-z][A-Za-z0-9_-]*`. */
    public val name: String,
    /** Display name. Defaults to [name]. */
    public val title: String,
    /** One paragraph describing the role. */
    public val summary: String?,
    /** Examples, in the order `example()` was called. */
    public val examples: List<RoleExample>,
    /**
     * Whether this role is rendered into the generated documentation.
     *
     * The declared value is kept as written: it is not merged with the group's value.
     */
    public val documented: Boolean,
    /**
     * The `layout { }` blocks of this role, in declaration order. They have not been
     * evaluated. A role may declare several, one per place its files may live.
     */
    public val layouts: List<LayoutDeclaration>,
    /** Names of the groups this role sits in, outermost first. */
    public val groupPath: List<String>,
    /** Where `"Name" { }` was written. */
    public val declaredAt: DeclarationSite,
) {
    /** [groupPath] and [name] joined with `/`, e.g. `domain/UseCase`. */
    public val qualifiedName: String = (groupPath + name).joinToString("/")

    override fun toString(): String = "Role($qualifiedName)"
}
