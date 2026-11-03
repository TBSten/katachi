package me.tbsten.katachi.dsl.internal

internal class SpecifierCandidate(
    val roleQualifiedName: String,
    val roleName: String,
    val id: String?,
) {
    val specifier: String = if (id == null) roleQualifiedName else "$roleQualifiedName.$id"
}

internal sealed interface SpecifierResolution {
    class Found(val index: Int) : SpecifierResolution

    data object Unknown : SpecifierResolution

    class Ambiguous(val specifiers: List<String>) : SpecifierResolution
}

// The one place the specifier rule is written (dsl cannot import template).
// Neither reading guesses a group from a suffix.
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

private fun rolesMatching(reference: String, candidates: List<SpecifierCandidate>): List<String> {
    candidates.firstOrNull { it.roleQualifiedName == reference }?.let { return listOf(it.roleQualifiedName) }
    return candidates.filter { it.roleName == reference }.map { it.roleQualifiedName }.distinct()
}
