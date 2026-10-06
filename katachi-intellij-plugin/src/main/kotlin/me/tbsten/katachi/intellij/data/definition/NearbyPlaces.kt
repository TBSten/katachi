package me.tbsten.katachi.intellij.data.definition

import kotlin.math.abs
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.NearbyRole
import me.tbsten.katachi.intellij.model.RoleModel

/** How many places [DefinitionMatcher] offers for a file nobody holds, as katachi's `[UnexpectedFile]` block does. */
private const val NEARBY_LIMIT: Int = 3

/**
 * The places a file could be moved to, and the order they are offered in: a port of katachi's `NearbyCandidates`
 * (design violations-output 2.2 and 2.3), so the notification names the places the `[UnexpectedFile]` block names.
 *
 * A place is a directory a role declared a file in, or wrote `anyFile()` in, with the file name pattern it declared
 * there. A directory that only holds another directory, one the role `ignore()`s, the project root, and what a
 * `module { }` block injected (`synthetic`) or `gradle()` declared (a role of the `Gradle` group) are not places. A pattern with two `**` has no single way of being filled in.
 *
 * For one file, every place is filled in with the file's own path ([instantiate]), and the ones that could hold a file
 * of its name ([acceptsExtension]) and start where the file starts are ordered by the levels that differ, then whether
 * the name fits the pattern, then the characters that differ, then declaration order. Three are kept. The cut-offs
 * (the level count bounds the distance from below; the characters are only counted on a tie) do not change the answer:
 * `MatcherReference` of the property tests keeps none of them and has to agree.
 *
 * ```kotlin
 * NearbyPlaces(slots).nearbyOf("core/domain/usecase/GetUserUseCase.kt") // domain.UseCase -> core/domain/useCase, ...
 * ```
 */
internal class NearbyPlaces(entries: List<Pair<RoleModel, LayoutEntryModel>>) {
    private val candidates: List<Candidate> = entries.candidates()

    fun nearbyOf(file: String): List<NearbyRole> {
        val directory = file.parentPath()
        // The root is not a directory any role can declare, so a file there has no neighbour.
        if (directory.isEmpty()) return emptyList()
        val levels = directory.split('/')
        val name = file.substringAfterLast('/')
        val best = ArrayList<Ranked>(NEARBY_LIMIT + 1)
        for (candidate in candidates) {
            if (!acceptsExtension(candidate.filePattern, name)) continue
            val filled = instantiate(candidate.segments, levels) ?: continue
            if (filled.firstOrNull() != levels[0]) continue
            // Only the three nearest are wanted, and the level count alone bounds the distance from below.
            val third = best.getOrNull(NEARBY_LIMIT - 1)
            if (third != null && abs(filled.size - levels.size) > third.segments) continue
            val ranked = Ranked(candidate, filled, levels, name)
            val same = best.indexOfFirst { it.candidate.role.qualifiedName == candidate.role.qualifiedName && it.directory == filled }
            if (same >= 0) {
                // One line per role and directory: `X.kt` and `*.kt` in one directory are offered once, with whichever came out nearer.
                if (RANKING.compare(ranked, best[same]) >= 0) continue
                best.removeAt(same)
            } else if (third != null && RANKING.compare(ranked, third) >= 0) {
                continue
            }
            val position = best.indexOfFirst { RANKING.compare(ranked, it) < 0 }
            best.add(if (position < 0) best.size else position, ranked)
            if (best.size > NEARBY_LIMIT) best.removeAt(best.size - 1)
        }
        return best.map { NearbyRole(it.candidate.role, it.directory.joinToString("/"), it.candidate.entry) }
    }
}

/** One declared place, read once: the role, its directory pattern by level, and its file name pattern (`null` for `anyFile()`). */
private class Candidate(
    val role: RoleModel,
    val entry: LayoutEntryModel,
    val segments: List<NearbySegment>,
    val filePattern: String?,
    val order: Int,
) {
    private val fileGlob: KatachiGlob? = filePattern?.let { KatachiGlob.compile(it) }

    fun matchesName(name: String): Boolean = fileGlob?.matches(name) ?: true
}

/** A candidate filled in for one file, with its distances; the characters are only counted on a tie. */
private class Ranked(val candidate: Candidate, val directory: List<String>, fileDirectory: List<String>, fileName: String) {
    val segments: Int = segmentDistance(fileDirectory, directory)
    val nameMismatch: Int = if (candidate.matchesName(fileName)) 0 else 1
    val characters: Int by lazy { characterDistance(fileDirectory, directory) }
}

private val RANKING: Comparator<Ranked> = compareBy({ it.segments }, { it.nameMismatch }, { it.characters }, { it.candidate.order })

/**
 * Whether `gradle()` declared the role. The definition JSON carries no `DeclaredByGradle`, so the group stands in:
 * every role `gradle()` declares sits under the `Gradle` group (`Gradle.BuildScript`, `Gradle.GradleWrapper.WrapperJar`, ...).
 */
private fun RoleModel.isDeclaredByGradle(): Boolean = groupPath.firstOrNull() == GRADLE_GROUP

private const val GRADLE_GROUP: String = "Gradle"

private fun List<Pair<RoleModel, LayoutEntryModel>>.candidates(): List<Candidate> {
    val seen = LinkedHashMap<Triple<String, String, String?>, Candidate>()
    for ((role, entry) in this) {
        if (entry.synthetic || role.isDeclaredByGradle()) continue
        val directory: String
        val filePattern: String?
        when (entry.kind) {
            LayoutEntryKind.File -> {
                directory = entry.pattern.parentPath()
                filePattern = entry.pattern.substringAfterLast('/')
            }

            LayoutEntryKind.AnyFile -> {
                directory = entry.pattern
                filePattern = null
            }

            LayoutEntryKind.Directory, LayoutEntryKind.Ignore, is LayoutEntryKind.Unknown -> continue
        }
        if (directory.isEmpty()) continue
        val segments = nearbySegmentsOf(directory)
        if (segments.count { it == NearbySegment.DoubleStar } > 1) continue
        seen.getOrPut(Triple(role.qualifiedName, directory, filePattern)) { Candidate(role, entry, segments, filePattern, order = seen.size) }
    }
    return seen.values.toList()
}
