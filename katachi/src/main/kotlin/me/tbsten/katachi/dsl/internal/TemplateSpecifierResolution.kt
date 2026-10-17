package me.tbsten.katachi.dsl.internal

/**
 * One template a specifier can name: the role it belongs to and its id.
 *
 * [roleQualifiedName] is unique per role, so it also tells two roles of the same [roleName] apart.
 */
internal class SpecifierCandidate(
    val roleQualifiedName: String,
    val roleName: String,
    val id: String?,
) {
    /** The complete specifier of this template: the role's qualified name, then `.id` when it has one. */
    val specifier: String = if (id == null) roleQualifiedName else "$roleQualifiedName.$id"
}

/** What [resolveSpecifier] made of a specifier. */
internal sealed interface SpecifierResolution {
    /** The specifier names exactly the candidate at [index]. */
    class Found(val index: Int) : SpecifierResolution

    /** The specifier names no candidate. */
    data object Unknown : SpecifierResolution

    /** The specifier names more than one candidate; [specifiers] are theirs, sorted. */
    class Ambiguous(val specifiers: List<String>) : SpecifierResolution
}

/**
 * Reads [spec] against [candidates], the way `--arg template=` and `template("...")` of a template
 * set both read a specifier. It is the one place that rule is written, because `dsl` cannot import
 * `template`.
 *
 * [spec] is tried whole, as a reference to a role with exactly one template, and once more with its
 * last `.`-segment read as an id. Neither reading guesses a group from a suffix: a specifier that
 * leaves its group out only resolves when the plain role name picks exactly one templated role.
 */
internal fun resolveSpecifier(spec: String, candidates: List<SpecifierCandidate>): SpecifierResolution {
    val wholeRoleMatches = rolesMatching(spec, candidates)
    val templatesOfWholeRole = wholeRoleMatches.singleOrNull()
        ?.let { role -> candidates.indices.filter { candidates[it].roleQualifiedName == role } }
        .orEmpty()
    val readAsRole = templatesOfWholeRole.singleOrNull()

    val lastDot = spec.lastIndexOf('.')
    val readAsRoleAndId = if (lastDot in 1 until spec.lastIndex + 1) {
        val rolePart = spec.substring(0, lastDot)
        val idPart = spec.substring(lastDot + 1)
        rolesMatching(rolePart, candidates).singleOrNull()
            ?.let { role -> candidates.indices.firstOrNull { candidates[it].roleQualifiedName == role && candidates[it].id == idPart } }
    } else {
        null
    }

    val hits = listOfNotNull(readAsRole, readAsRoleAndId).distinctBy { candidates[it].specifier }
    return when {
        hits.size == 1 -> SpecifierResolution.Found(hits.single())
        hits.size > 1 -> SpecifierResolution.Ambiguous(hits.map { candidates[it].specifier }.sorted())
        templatesOfWholeRole.size > 1 -> SpecifierResolution.Ambiguous(templatesOfWholeRole.map { candidates[it].specifier }.sorted())
        wholeRoleMatches.size > 1 -> SpecifierResolution.Ambiguous(
            wholeRoleMatches
                .flatMap { role -> candidates.filter { it.roleQualifiedName == role } }
                .map { it.specifier }
                .sorted(),
        )
        else -> SpecifierResolution.Unknown
    }
}

/**
 * The qualified names of the roles [reference] could name: an exact qualified-name match first (at
 * most one), and only when none matches that way every templated role sharing that plain name.
 */
private fun rolesMatching(reference: String, candidates: List<SpecifierCandidate>): List<String> {
    candidates.firstOrNull { it.roleQualifiedName == reference }?.let { return listOf(it.roleQualifiedName) }
    return candidates.filter { it.roleName == reference }.map { it.roleQualifiedName }.distinct()
}
