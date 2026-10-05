package me.tbsten.katachi.intellij.model

import java.nio.file.Path

/**
 * The answer to "which role holds this file" (design 4.5): what the layout entries and file
 * constraints of a definition say about one file. The two features read it; it has no IntelliJ type.
 *
 * ```kotlin
 * val roles = service.forFile(file) ?: return
 * val names = roles.matches.map { it.role.qualifiedName }
 * ```
 */
internal data class FileRoles(
    val module: KatachiModule,
    val projectRoot: Path,
    /** Relative to [projectRoot], `/` separated. */
    val relativePath: String,
    /** One per role, the roles that named the file first, then the roles that only left its directory open. Empty when no role holds it. */
    val matches: List<EntryMatch>,
    /** The constraints of the roles in [matches] that cover this file. */
    val constraints: List<ConstraintMatch>,
    val outcome: MatchOutcome,
    /** Only for [MatchOutcome.Unexpected] and [MatchOutcome.UnexpectedUnder]: places the file could move to, nearest first. */
    val nearby: List<NearbyRole>,
    val freshness: Freshness,
)

/**
 * What the matcher says of a path alone, before it is tied to a module and a freshness. Everything
 * [FileRoles] has except where the answer came from.
 */
internal data class FileMatch(
    val matches: List<EntryMatch>,
    val constraints: List<ConstraintMatch>,
    val outcome: MatchOutcome,
    val nearby: List<NearbyRole>,
) {
    /** [this] as the answer for [relativePath] of [module]'s definition. */
    fun within(module: KatachiModule, projectRoot: Path, relativePath: String, freshness: Freshness): FileRoles =
        FileRoles(module, projectRoot, relativePath, matches, constraints, outcome, nearby, freshness)
}

internal data class EntryMatch(val role: RoleModel, val entry: LayoutEntryModel)

internal data class ConstraintMatch(val role: RoleModel, val constraint: ConstraintModel)

/** A place [directory] (relative to the project root) where [role] accepts a file, offered for a file nobody holds. */
internal data class NearbyRole(val role: RoleModel, val directory: String, val entry: LayoutEntryModel)

/** Why a file has the roles it has (or none). */
internal sealed interface MatchOutcome {
    /** At least one role holds the file. */
    data object Matched : MatchOutcome

    /** A directory above the file is `ignore()`d by [entry]; katachi never looks in it. */
    data class Ignored(val entry: LayoutEntryModel) : MatchOutcome

    /**
     * [directory] (a directory above the file) is one katachi never descends into whatever the definition says: `.git`, `.gradle`
     * or `.idea` at any depth. No role holds the file and nothing is expected of it.
     */
    data class NotWalked(val directory: String) : MatchOutcome

    /** The version control ignores the file and `files = gitTracked()`, so katachi does not see it. Untracked is not here (design 4.4). */
    data object IgnoredByVcs : MatchOutcome

    /** No role holds the file, and every directory above it is one a role declared. */
    data object Unexpected : MatchOutcome

    /** No role holds the file, and [directory] (the first one above it no role declared) is where the check stops. */
    data class UnexpectedUnder(val directory: String) : MatchOutcome
}

/** The explanation of one file under a `ktFileContent` constraint of [role], for the gutter (design 4.5). */
internal data class RoleFileDeclarations(
    val role: RoleModel,
    /** A constraint whose backend is `ktFileContent`. */
    val constraint: ConstraintModel,
    /** The one file's part of [ConstraintModel.fileDeclarations]. */
    val declarations: FileDeclarationsModel,
    val freshness: Freshness,
)
