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
 * Throws [DuplicateDeclarationException] when a group named [name] was already declared
 * directly under [parentPath].
 *
 * Only direct siblings are compared: the same group name may appear under two different
 * parents, because the pair (parent, name) is what identifies a group.
 */
internal fun requireNoDuplicateGroup(
    siblings: List<Group>,
    name: String,
    parentPath: List<String>,
    declaredAt: DeclarationSite,
) {
    val first = siblings.firstOrNull { it.name == name } ?: return
    val parent = if (parentPath.isEmpty()) "the root of architecture { }" else "group \"${parentPath.joinToString("/")}\""
    throw DuplicateDeclarationException(
        name = name,
        firstDeclaredAt = first.declaredAt,
        declaredAt = declaredAt,
        message = buildString {
            appendLine("Duplicate group \"$name\" declared at $declaredAt.")
            appendLine("It was already declared at ${first.declaredAt}, directly under $parent.")
            append("Group names must be unique among the groups declared under the same parent.")
        },
    )
}

/**
 * Throws [DuplicateDeclarationException] when a role named [name] was already declared in
 * the group at [groupPath].
 *
 * Uniqueness is scoped to one group: the same role name in a different group is fine,
 * since the generated documentation pages do not collide.
 */
internal fun requireNoDuplicateRole(
    siblings: List<Role>,
    name: String,
    groupPath: List<String>,
    declaredAt: DeclarationSite,
) {
    val first = siblings.firstOrNull { it.name == name } ?: return
    throw DuplicateDeclarationException(
        name = name,
        firstDeclaredAt = first.declaredAt,
        declaredAt = declaredAt,
        message = buildString {
            appendLine(
                "Duplicate role \"$name\" in group \"${groupPath.joinToString("/")}\" " +
                    "declared at $declaredAt.",
            )
            appendLine("It was already declared at ${first.declaredAt}.")
            append(
                "Role names must be unique within a group. The same name may be reused " +
                    "in a different group.",
            )
        },
    )
}
