package me.tbsten.katachi.dsl

/**
 * A group of roles. One group is one documentation output directory; it says nothing
 * about where the files physically live, so the roles of one group may be spread over
 * several modules. Groups can nest.
 */
public class Group internal constructor(
    /** Identifier. Matches `[A-Za-z][A-Za-z0-9_-]*`. */
    public val name: String,
    /** Display name. Defaults to [name]. */
    public val title: String,
    /**
     * Whether this group is rendered into the generated documentation.
     *
     * The declared value is kept as written: it is not merged with the parent's value.
     */
    public val documented: Boolean,
    /** Names from the outermost group down to this one. */
    public val path: List<String>,
    /** Nested groups, in declaration order. */
    public val groups: List<Group>,
    /** Roles declared directly in this group, in declaration order. */
    public val roles: List<Role>,
    /** Where `"name".group { }` was written. */
    public val declaredAt: DeclarationSite,
) {
    /** [path] joined with `/`, e.g. `domain/model`. */
    public val qualifiedName: String = path.joinToString("/")

    internal fun selfAndDescendants(): List<Group> =
        listOf(this) + groups.flatMap { it.selfAndDescendants() }

    override fun toString(): String = "Group($qualifiedName)"
}
