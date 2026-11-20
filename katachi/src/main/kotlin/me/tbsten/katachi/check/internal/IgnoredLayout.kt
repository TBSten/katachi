package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/**
 * A line of a role's `layout { }` every file of which lies in a directory an `ignore()` hides, so
 * none of them is ever walked and none of the role's constraints is checked against them.
 *
 * Internal: the label, the path, the kind and the severity are what a test reads through
 * [Violation], and the report block says the rest.
 */
internal class IgnoredLayout internal constructor(
    /** The line's path, as the role wrote it: a pattern, or the directory of an `anyFile()`. */
    override val path: String,
    /** The role the line belongs to. */
    val role: Role,
    /** Where the line was written. */
    val declaredAt: DeclarationSite,
    /** Every `ignore()` that hides all of the line on its own, in declaration order. Never empty. */
    val hiddenBy: List<HidingIgnore>,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Ambiguous
    override val severity: Severity get() = Severity.Warning
    override val label: String get() = "IgnoredLayout"
    /** `[<label>] <path>`, for a log line or a test failure message. */
    override fun toString(): String = "[$label] $path"
}

/** One `ignore()` an [IgnoredLayout] is hidden by. */
internal class HidingIgnore(
    /** The ignored directory, as declared. */
    val path: String,
    /** The role that declared it. */
    val role: Role,
    /** Where it was declared; for [gradleModule], where `gradle()` was called. */
    val declaredAt: DeclarationSite,
    /**
     * The module whose build directory this is, when `gradle()` ignored it on its own rather than
     * anyone writing `ignore()`; `null` otherwise.
     */
    val gradleModule: String?,
) {
    /** [gradleModule] as a report names it: `module :app`, or `the root project`. */
    val gradleModuleName: String?
        get() = gradleModule?.let { if (it == ":") "the root project" else "module $it" }
}
