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

/**
 * A place [directory] (relative to the project root, the file's own path filled into every wildcard of the declaration)
 * where [role] accepts a file, offered for a file nobody holds. [entry] is the declaration the place comes from.
 */
internal data class NearbyRole(val role: RoleModel, val directory: String, val entry: LayoutEntryModel) {
    /** The file name pattern the role declared there (`*Screen.kt`), or `null` when it wrote `anyFile()`. */
    val filePattern: String? get() = if (entry.kind == LayoutEntryKind.File) entry.pattern.substringAfterLast('/') else null
}

/** Why a file has the roles it has (or none). */
internal sealed interface MatchOutcome {
    /** At least one role holds the file. */
    data object Matched : MatchOutcome

    /** A directory above the file is `ignore()`d by [entry]; katachi never looks in it. */
    data class Ignored(val entry: LayoutEntryModel) : MatchOutcome

    /**
     * [directory] (a directory above the file, or the file itself) is named `.git`, the one name katachi never walks into
     * whatever the definition says, at any depth. No role holds the file and nothing is expected of it.
     */
    data class NotWalked(val directory: String) : MatchOutcome

    /** The version control ignores the file and `files = gitTracked()`, so katachi does not see it. Untracked is not here (design 4.4). */
    data object IgnoredByVcs : MatchOutcome

    /** No role holds the file, and every directory above it is one a role declared. */
    data object Unexpected : MatchOutcome

    /** No role holds the file, and [directory] (the first one above it no role declared) is where the check stops. */
    data class UnexpectedUnder(val directory: String) : MatchOutcome

    /**
     * No role holds the file, and the pattern of [entry] of [role] matches it but its bound captures do not agree: the
     * levels named alike read different values, or a derived level is not the conversion of its source (design
     * capture-check 3.8; katachi's `[CaptureMismatch]`). The first entry declared that held the file back. [expectedPath] is
     * the path the file would have if every level read what the first plain occurrence of its name reads, `null` when a
     * value it needs could not be read. No nearby places: the one place is [expectedPath].
     */
    data class CaptureMismatch(
        val role: RoleModel,
        val entry: LayoutEntryModel,
        val occurrences: List<CaptureOccurrenceModel>,
        val expectedPath: String?,
    ) : MatchOutcome
}

/**
 * One occurrence of a bound capture name on a file, as `[CaptureMismatch]` lists it: [level] is the 0-based level of the
 * entry's pattern, [value] what the file holds there, [expected] what it was held to (`null` for the first plain
 * occurrence, whose value the others are held to). [transform] is the conversion of a derived occurrence.
 */
internal data class CaptureOccurrenceModel(val name: String, val transform: String?, val level: Int, val value: String, val expected: String?)

/** The explanation of one file under a `ktFileContent` constraint of [role], for the gutter (design 4.5). */
internal data class RoleFileDeclarations(
    val role: RoleModel,
    /** A constraint whose backend is `ktFileContent`. */
    val constraint: ConstraintModel,
    /** The one file's part of [ConstraintModel.fileDeclarations]. */
    val declarations: FileDeclarationsModel,
    val freshness: Freshness,
)

/** What a [DeclarationAt] is, by the kind of thing the definition declared there. */
internal enum class DeclarationKind {
    Architecture,
    Group,
    Role,
    Entry,

    /** The `.template { }` of an entry: its site is the template's own, [DeclarationAt.entry] holds the [TemplateRef]. */
    Template,
    Constraint,
}

/**
 * One thing the definition says, with where it was written (ide-authoring design 4.2): the definition file's side of
 * [FileRoles]. Exactly the members its [kind] needs are set: `group` for a group, `role` for a role, an entry, a template and a
 * constraint, `entry` for an entry and a template, `constraint` for a constraint.
 *
 * ```kotlin
 * val roles = service.declarationsIn(definitionFile).filter { it.kind == DeclarationKind.Role }.map { it.role!!.qualifiedName }
 * ```
 */
internal data class DeclarationAt(
    val module: ModuleId,
    val site: DeclarationSiteRef,
    val kind: DeclarationKind,
    val group: GroupModel? = null,
    val role: RoleModel? = null,
    val entry: LayoutEntryModel? = null,
    val constraint: ConstraintModel? = null,
)
