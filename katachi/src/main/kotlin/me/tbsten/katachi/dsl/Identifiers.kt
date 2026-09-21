package me.tbsten.katachi.dsl

/**
 * The character set every group name and role name must follow.
 *
 * Names double as file names and as reference keys, so they stay ASCII and stable. The
 * human readable form belongs in `title`.
 */
private val IDENTIFIER_PATTERN: Regex = Regex("[A-Za-z][A-Za-z0-9_-]*")

/** Throws [KatachiInvalidIdentifierException] unless [name] matches [IDENTIFIER_PATTERN]. */
internal fun requireValidIdentifier(
    name: String,
    kind: DeclarationKind,
    declaredAt: DeclarationSite,
) {
    if (IDENTIFIER_PATTERN.matches(name)) return
    throw KatachiInvalidIdentifierException(kind = kind, name = name, declaredAt = declaredAt)
}

/** What took a name: which kind of declaration it was, and where it was written. */
internal class NameReservation(
    val kind: DeclarationKind,
    val declaredAt: DeclarationSite,
)

/**
 * The names already taken in one namespace, and what took each of them.
 *
 * A name is reserved *before* its own block runs, not after. A block can reach the scope
 * that encloses it — by capturing that receiver into a local `val`, for instance — and
 * declare into it again while the first declaration is still being built. Comparing
 * against finished siblings would see an empty list at that moment and let the duplicate
 * through.
 *
 * Which declarations share one instance is the scope's decision, and the two scopes decide
 * differently: `architecture { }` reserves groups and roles in a single instance, a group
 * block keeps one for each. See `ArchitectureScopeImpl` for why the root shares.
 */
internal class DeclaredNames {
    private val reservations = mutableMapOf<String, NameReservation>()

    /** What took [name] the first time, or `null` if it is still free. */
    fun firstReservationOf(name: String): NameReservation? = reservations[name]

    /** Takes [name]. Call this before evaluating the declaration's block. */
    fun reserve(name: String, kind: DeclarationKind, declaredAt: DeclarationSite) {
        reservations[name] = NameReservation(kind = kind, declaredAt = declaredAt)
    }
}

/**
 * Throws [KatachiDuplicateDeclarationException] when [name] is already taken in
 * [declaredNames], which holds the names of the scope at [path].
 *
 * Only one scope is compared, never a parent or a child: the same group name may appear
 * under two different parents, and the same role name in two different groups, because in
 * both cases the qualified name — and with it the generated documentation page — differs.
 */
internal fun requireNameIsFree(
    declaredNames: DeclaredNames,
    kind: DeclarationKind,
    name: String,
    path: List<String>,
    declaredAt: DeclarationSite,
) {
    val first = declaredNames.firstReservationOf(name) ?: return
    throw KatachiDuplicateDeclarationException(
        kind = kind,
        firstKind = first.kind,
        name = name,
        scope = scopeOf(path),
        firstDeclaredAt = first.declaredAt,
        declaredAt = declaredAt,
    )
}

/** The scope a duplicate collided in: the architecture itself, or one named group. */
private fun scopeOf(path: List<String>): String =
    if (path.isEmpty()) "the root of architecture { }" else "group \"${path.joinToString("/")}\""
