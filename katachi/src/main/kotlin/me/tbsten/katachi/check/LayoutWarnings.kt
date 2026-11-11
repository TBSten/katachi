package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/**
 * One role's claim on a path, as an [AmbiguousLayout] block lists it.
 *
 * ## Example 1: read who claims an ambiguous path
 * ```kt
 * val ambiguous = projectArchitecture.assertNoErrors().filterIsInstance<AmbiguousLayout>().first()
 * ambiguous.claims.map { "${it.role.qualifiedName} (${it.declaredAt})" }
 * ```
 */
public class LayoutClaim internal constructor(
    /** The role making this claim. */
    public val role: Role,
    /** Where the role declared this path. */
    public val declaredAt: DeclarationSite,
) {
    /** `<role> (<declaredAt>)`, for a log line or a test failure message. */
    override fun toString(): String = "${role.qualifiedName} ($declaredAt)"
}

/**
 * Two or more roles claim the same thing, so a file there belongs to every one of them at once
 * and which of them it is actually for is not decided.
 *
 * It is raised two ways, told apart by [overlappingFiles][AmbiguousLayout.overlappingFiles]:
 * the roles wrote the **same pattern text**, or their patterns differ but land on the **same real
 * files** (`"*.kt"` against `"*ViewModel.kt"` in one directory).
 *
 * Such a file is allowed, but every file constraint of every one of those roles is checked
 * against it. katachi never raises this to [Severity.Error]: an intentional overlap is not a
 * mistake.
 *
 * ## Example 1: list the paths more than one role claims outright
 * ```kt
 * projectArchitecture.assertNoErrors().filterIsInstance<AmbiguousLayout>()
 *     .associate { it.path to it.claims.map { claim -> claim.role.qualifiedName } }
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
     */
    public val claims: List<LayoutClaim>,
    /**
     * Every existing file all of the claims cover, [path][AmbiguousLayout.path] first.
     *
     * Empty when the roles only wrote the same pattern text.
     *
     * One warning covers the whole group however long this list is, so read it to find the rest.
     */
    public val overlappingFiles: List<String>,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Ambiguous
    override val severity: Severity get() = Severity.Warning
    override val label: String get() = "AmbiguousLayout"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}
