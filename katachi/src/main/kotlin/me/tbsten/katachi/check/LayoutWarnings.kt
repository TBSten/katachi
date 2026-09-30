package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/**
 * One role's claim on a path, as an [AmbiguousLayout] block lists it.
 *
 * ## Example 1: read who claims an ambiguous path
 * ```kt
 * val ambiguous = projectArchitecture.assertNoErrors().filterIsInstance<AmbiguousLayout>().first()
 * ambiguous.claims.map { it.role.qualifiedName }
 * ```
 */
public class LayoutClaim internal constructor(
    /**
     * The role making this claim.
     *
     * ## Example 1: read which role made one claim
     * ```kt
     * val ambiguous = projectArchitecture.assertNoErrors()
     *     .filterIsInstance<AmbiguousLayout>().first()
     * ambiguous.claims.first().role.qualifiedName
     * ```
     */
    public val role: Role,
    /**
     * Where the role declared this path.
     *
     * ## Example 1: point a report back at the line that declared one claim
     * ```kt
     * val ambiguous = projectArchitecture.assertNoErrors()
     *     .filterIsInstance<AmbiguousLayout>().first()
     * ambiguous.claims.first().declaredAt
     * ```
     */
    public val declaredAt: DeclarationSite,
) {
    /** `<role> (<declaredAt>)`, for a log line or a test failure message. */
    override fun toString(): String = "${role.qualifiedName} ($declaredAt)"
}

/**
 * Two or more roles claim the same thing, so a file there belongs to every one of them at once
 * and which of them it is actually for is not decided.
 *
 * It is raised two ways, and [overlappingFiles][AmbiguousLayout.overlappingFiles] is how they are
 * told apart. Either the roles wrote the **same pattern text**, which the declarations say by
 * themselves and which holds whether or not a file is there yet; or their patterns are different
 * but a walk of the project found them landing on the **same real files**, which is the case
 * `"*.kt"` against `"*ViewModel.kt"` in one directory produces and which no comparison of the text
 * could see.
 *
 * A file claimed by more than one role is allowed — a file is fine as long as *some* role allows
 * it — but every `konsist { }` of every one of those roles is then checked against it, which is
 * what makes an overlap nobody meant worth a warning.
 *
 * Katachi never raises this to [Severity.Error]: an intentional overlap is not a mistake, and
 * deciding which role a shared path belongs to is a judgment call the definitions' authors have
 * to make, not something a check can resolve on their behalf.
 *
 * ## Example 1: list the paths more than one role claims outright
 * ```kt
 * projectArchitecture.assertNoErrors().filterIsInstance<AmbiguousLayout>().map { it.path }
 * ```
 *
 * ## Example 2: tell the two ways it is raised apart
 * ```kt
 * val (onFiles, onText) = projectArchitecture.assertNoErrors()
 *     .filterIsInstance<AmbiguousLayout>()
 *     .partition { it.overlappingFiles.isNotEmpty() }
 * ```
 */
public class AmbiguousLayout internal constructor(
    override val path: String,
    /**
     * The roles claiming [path][AmbiguousLayout.path], in declaration order. Always two or more.
     *
     * When [overlappingFiles][AmbiguousLayout.overlappingFiles] is not empty, a role that named the
     * file comes before one that only left its directory open with `anyFile()`, and the declaration
     * order holds within each of those two groups.
     *
     * ## Example 1: read every role that claims one ambiguous path
     * ```kt
     * projectArchitecture.assertNoErrors().filterIsInstance<AmbiguousLayout>().first().claims
     * ```
     */
    public val claims: List<LayoutClaim>,
    /**
     * Every file the walk found all of [claims][AmbiguousLayout.claims] claiming, in walk order,
     * with [path][AmbiguousLayout.path] first — or empty when this was raised from the declarations
     * alone.
     *
     * Empty is the exact statement "no walk found this": the roles wrote the same pattern text, and
     * whether a file sits at it was never asked. Non-empty means the opposite — these files exist
     * and each of them belongs to all of [claims][AmbiguousLayout.claims] at once. One warning
     * covers the whole group however long this list is, so reading it is how a caller finds the
     * rest.
     *
     * ## Example 1: count the files an overlap actually affects
     * ```kt
     * projectArchitecture.assertNoErrors()
     *     .filterIsInstance<AmbiguousLayout>()
     *     .associate { it.path to it.overlappingFiles.size }
     * ```
     */
    public val overlappingFiles: List<String>,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Ambiguous
    override val severity: Severity get() = Severity.Warning
    override val label: String get() = "AmbiguousLayout"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}

/**
 * A role that may live in more than one place, with one of those places saying nothing about
 * when it is the right one.
 *
 * A place is a directory a `"...".module { }` key opened for the role. One place needs no
 * explanation: everything the role owns is there. From two on, a reader deciding where to put a new
 * file has a choice to make, and `description = "..."` is the sentence that makes it for them.
 *
 * Katachi never raises this to [Severity.Error]: a definition with the sentence missing is
 * accurate, only terse, and the file it would have guided is still allowed exactly where the
 * layout says it is.
 *
 * ## Example 1: list the places that still owe an explanation
 * ```kt
 * projectArchitecture.assertNoErrors().filterIsInstance<MissingDescription>().map { it.path }
 * ```
 */
public class MissingDescription internal constructor(
    override val path: String,
    /**
     * The role that may live at [path][MissingDescription.path].
     *
     * ## Example 1: read which role left a place unexplained
     * ```kt
     * projectArchitecture.assertNoErrors()
     *     .filterIsInstance<MissingDescription>().first().role.qualifiedName
     * ```
     */
    public val role: Role,
    /**
     * The role's other places, in declaration order. Never empty — a role with one place is
     * never reported.
     *
     * ## Example 1: read what a place has to be told apart from
     * ```kt
     * projectArchitecture.assertNoErrors()
     *     .filterIsInstance<MissingDescription>().first().otherPlaces
     * ```
     */
    public val otherPlaces: List<String>,
    /**
     * Where the block that should carry the description was opened.
     *
     * ## Example 1: point back at the block a description belongs in
     * ```kt
     * projectArchitecture.assertNoErrors()
     *     .filterIsInstance<MissingDescription>().first().declaredAt
     * ```
     */
    public val declaredAt: DeclarationSite,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Unexplained
    override val severity: Severity get() = Severity.Warning
    override val label: String get() = "MissingDescription"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}

