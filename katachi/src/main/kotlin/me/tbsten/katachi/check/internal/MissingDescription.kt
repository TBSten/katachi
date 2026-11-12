package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/**
 * A role that may live in more than one place, with one of those places saying nothing about
 * when it is the right one.
 *
 * Internal: a place is a directory a module key opens, and since `.module { }` was removed no
 * public declaration gives a role two places, so this warning is not raised. The type stays so
 * the detection, the report and SARIF keep working if a way to write places comes back.
 */
internal class MissingDescription internal constructor(
    override val path: String,
    /** The role that may live at [path][MissingDescription.path]. */
    val role: Role,
    /**
     * The role's other places, in declaration order. Never empty — a role with one place is
     * never reported.
     */
    val otherPlaces: List<String>,
    /** Where the block that should carry the description was opened. */
    val declaredAt: DeclarationSite,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Unexplained
    override val severity: Severity get() = Severity.Warning
    override val label: String get() = "MissingDescription"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}

