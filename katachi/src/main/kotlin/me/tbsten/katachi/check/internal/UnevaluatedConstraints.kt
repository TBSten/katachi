package me.tbsten.katachi.check.internal

import me.tbsten.katachi.dsl.internal.DeclaredConstraint
import me.tbsten.katachi.dsl.internal.reportPath
import me.tbsten.katachi.processor.internal.ProjectWalk
import me.tbsten.katachi.scan.UncheckedConstraint
import me.tbsten.katachi.scan.UncheckedConstraintReason
import me.tbsten.katachi.scan.Violation

/** One [UncheckedConstraint], built from what the declaration already knows. */
internal fun uncheckedConstraintOf(
    declared: DeclaredConstraint,
    reason: UncheckedConstraintReason,
    cause: Throwable?,
): UncheckedConstraint = UncheckedConstraint(
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
 * code breaking them, and a green test — when `KonsistCheck()` was left out of the
 * arguments. A project that declares no constraints gets an empty list, so a user who has
 * never written one never sees any of this.
 */
internal fun ProjectWalk.unevaluatedConstraintViolations(): List<Violation> =
    unevaluatedConstraints.map {
        uncheckedConstraintOf(it, UncheckedConstraintReason.NotEvaluated, cause = null)
    }
