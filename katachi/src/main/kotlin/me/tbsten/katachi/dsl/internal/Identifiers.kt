package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationKind
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException
import me.tbsten.katachi.dsl.KatachiInvalidIdentifierException

// ASCII and stable: names double as file names and reference keys; the readable form belongs in `title`.
private val IDENTIFIER_PATTERN: Regex = Regex("[A-Za-z][A-Za-z0-9_-]*")

internal fun requireValidIdentifier(
    name: String,
    kind: DeclarationKind,
    declaredAt: DeclarationSite,
) {
    if (IDENTIFIER_PATTERN.matches(name)) return
    throw KatachiInvalidIdentifierException(kind = kind, name = name, declaredAt = declaredAt)
}

internal class NameReservation(
    val kind: DeclarationKind,
    val declaredAt: DeclarationSite,
)

// Reserve before the block runs: a block reaching back into the enclosing scope could otherwise declare a
// duplicate while the first is being built.
internal class DeclaredNames {
    private val reservations = mutableMapOf<String, NameReservation>()

    fun firstReservationOf(name: String): NameReservation? = reservations[name]

    fun reserve(name: String, kind: DeclarationKind, declaredAt: DeclarationSite) {
        reservations[name] = NameReservation(kind = kind, declaredAt = declaredAt)
    }
}

// Compares one scope only: the same name under two parents (or roles in two groups) is fine because the qualified
// name differs.
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

private fun scopeOf(path: List<String>): String =
    if (path.isEmpty()) "the root of architecture { }" else "group \"${path.joinToString(".")}\""
