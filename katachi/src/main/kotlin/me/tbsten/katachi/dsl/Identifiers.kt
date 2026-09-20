package me.tbsten.katachi.dsl

/**
 * The character set every group name and role name must follow.
 *
 * Names double as file names and as reference keys, so they stay ASCII and stable. The
 * human readable form belongs in `title`.
 */
internal val IDENTIFIER_PATTERN: Regex = Regex("[A-Za-z][A-Za-z0-9_-]*")

/** What is being named. Only used to word the error messages. */
internal enum class IdentifierKind(val label: String) {
    Group("group"),
    Role("role"),
}

/** Throws [InvalidIdentifierException] unless [name] matches [IDENTIFIER_PATTERN]. */
internal fun requireValidIdentifier(
    name: String,
    kind: IdentifierKind,
    declaredAt: DeclarationSite,
) {
    if (IDENTIFIER_PATTERN.matches(name)) return
    throw InvalidIdentifierException(
        name = name,
        declaredAt = declaredAt,
        message = buildString {
            appendLine("Invalid ${kind.label} name \"$name\" declared at $declaredAt.")
            appendLine(
                "Names must match [A-Za-z][A-Za-z0-9_-]*: start with an ASCII letter, " +
                    "then ASCII letters, digits, '_' or '-'.",
            )
            append("Use `title` for a human readable display name.")
        },
    )
}

/**
 * The names already taken in one scope, and where each was declared.
 *
 * A name is reserved *before* its own block runs, not after. A block can reach the scope
 * that encloses it — by capturing that receiver into a local `val`, for instance — and
 * declare into it again while the first declaration is still being built. Comparing
 * against finished siblings would see an empty list at that moment and let the duplicate
 * through.
 */
internal class DeclaredNames {
    private val sites = mutableMapOf<String, DeclarationSite>()

    /** Where [name] was first declared, or `null` if it is still free. */
    fun firstSiteOf(name: String): DeclarationSite? = sites[name]

    /** Takes [name]. Call this before evaluating the declaration's block. */
    fun reserve(name: String, declaredAt: DeclarationSite) {
        sites[name] = declaredAt
    }
}

/**
 * Throws [DuplicateDeclarationException] when a group named [name] is already taken
 * directly under [parentPath].
 *
 * Only direct siblings are compared: the same group name may appear under two different
 * parents, because the pair (parent, name) is what identifies a group.
 */
internal fun requireNoDuplicateGroup(
    declaredNames: DeclaredNames,
    name: String,
    parentPath: List<String>,
    declaredAt: DeclarationSite,
) {
    val firstSite = declaredNames.firstSiteOf(name) ?: return
    val parent = if (parentPath.isEmpty()) "the root of architecture { }" else "group \"${parentPath.joinToString("/")}\""
    throw DuplicateDeclarationException(
        name = name,
        firstDeclaredAt = firstSite,
        declaredAt = declaredAt,
        message = buildString {
            appendLine("Duplicate group \"$name\" declared at $declaredAt.")
            appendLine("It was already declared at $firstSite, directly under $parent.")
            append("Group names must be unique among the groups declared under the same parent.")
        },
    )
}

/**
 * Throws [DuplicateDeclarationException] when a role named [name] is already taken in the
 * group at [groupPath].
 *
 * Uniqueness is scoped to one group: the same role name in a different group is fine,
 * since the generated documentation pages do not collide.
 */
internal fun requireNoDuplicateRole(
    declaredNames: DeclaredNames,
    name: String,
    groupPath: List<String>,
    declaredAt: DeclarationSite,
) {
    val firstSite = declaredNames.firstSiteOf(name) ?: return
    throw DuplicateDeclarationException(
        name = name,
        firstDeclaredAt = firstSite,
        declaredAt = declaredAt,
        message = buildString {
            appendLine(
                "Duplicate role \"$name\" in group \"${groupPath.joinToString("/")}\" " +
                    "declared at $declaredAt.",
            )
            appendLine("It was already declared at $firstSite.")
            append(
                "Role names must be unique within a group. The same name may be reused " +
                    "in a different group.",
            )
        },
    )
}
