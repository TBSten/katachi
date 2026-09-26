package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.dsl.internal.reportPath
import me.tbsten.katachi.processor.internal.ProjectWalk

/** One [UncheckedFileConstraint], built from what the declaration already knows. */
internal fun uncheckedFileConstraintOf(
    declared: DeclaredFileConstraint,
    reason: UncheckedFileConstraintReason,
    cause: Throwable?,
): UncheckedFileConstraint = UncheckedFileConstraint(
    path = declared.reportPath,
    reason = reason,
    role = declared.role,
    constraintName = declared.name,
    layoutPath = declared.layoutPath,
    declaredAt = declared.declaredAt,
    cause = cause,
)

/**
 * The constraints nobody evaluated, as violations.
 *
 * This is what closes the worst way this library could break — a definition full of rules,
 * code breaking them, and a green test — when `FileConstraintCheck()` was left out of the
 * arguments. A project that declares no constraints gets an empty list, so a user who has
 * never written one never sees any of this.
 */
internal fun ProjectWalk.unevaluatedFileConstraintViolations(): List<Violation> =
    unevaluatedFileConstraints.map {
        uncheckedFileConstraintOf(it, UncheckedFileConstraintReason.NotEvaluated, cause = null)
    }
