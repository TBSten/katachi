package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role

/** Whether a violation fails the check, or is only reported. */
public enum class Severity {
    /** `assert()` throws because of it. */
    Error,

    /** Reported next to the errors, but never the reason a check fails. */
    Warning,
}

/**
 * What kind of problem a violation is — which is the same thing as what the reader has to do
 * about it, so a report groups its blocks by this.
 */
public enum class ViolationKind {
    /** Something exists that no role allows. Delete it, move it, or declare it. */
    Unexpected,

    /** Something declared does not exist. Create it, or drop the declaration. */
    Missing,

    /** A constraint declared with `konsist { }` does not hold. Fix the code, or the constraint. */
    Constraint,
}

/**
 * One problem found by a check.
 *
 * The check returns these as a list rather than throwing, so that a later version can filter
 * them (a baseline), write them out (JSON / SARIF) or count them without any of it being a
 * rewrite. `assert()` is the thin layer on top that turns a non-empty list into a failure.
 *
 * A violation carries everything its report block needs and nothing else: the wording lives
 * in the report, not here.
 */
public sealed interface Violation {
    /** Which of the three problems this is. */
    public val kind: ViolationKind

    /** Whether this fails the check. */
    public val severity: Severity

    /**
     * The path the block's first line names, relative to the project root and `/` separated.
     * Where something actually is for [ViolationKind.Unexpected], where it was declared to be
     * for [ViolationKind.Missing].
     */
    public val path: String

    /** The `[...]` label of the block's first line, e.g. `UnexpectedFile`. */
    public val label: String
}

/** A place a role declared, offered as somewhere an unexpected file could move to. */
public class NearbyLocation internal constructor(
    /** The role that may put files there. */
    public val role: Role,
    /** The directory, relative to the project root. Never empty, never holds a wildcard. */
    public val directory: String,
) {
    override fun toString(): String = "${role.qualifiedName} -> $directory"
}

/** A file that exists although no role's layout allows it. */
public class UnexpectedFile internal constructor(
    override val path: String,
    /** Declared directories close to this file, nearest first. May be empty. */
    public val nearby: List<NearbyLocation>,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Unexpected
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UnexpectedFile"
    override fun toString(): String = "[$label] $path"
}

/**
 * A directory that exists although no role's layout mentions it.
 *
 * Reported instead of everything below it. Nothing inside a directory no role knows about
 * has a role either, so listing two hundred files would bury the one thing to fix, which is
 * the directory itself. The check does not descend past one of these.
 */
public class UnexpectedDirectory internal constructor(
    override val path: String,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Unexpected
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "UnexpectedDirectory"
    override fun toString(): String = "[$label] $path"
}

/**
 * A file a role declared, with nothing at that path.
 *
 * Only a declaration without a wildcard can end up here: a pattern describes a place that
 * fills up over time, and no matches is a normal state for one.
 */
public class MissingFile internal constructor(
    override val path: String,
    /** The role that declared the file. */
    public val role: Role,
    /** Where in the `layout { }` block it was declared. */
    public val declaredAt: DeclarationSite,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.Missing
    override val severity: Severity get() = Severity.Error
    override val label: String get() = "MissingFile"
    override fun toString(): String = "[$label] $path"
}
