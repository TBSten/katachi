package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.dsl.internal.reportPath
import me.tbsten.katachi.processor.internal.ProjectWalk

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

internal fun ProjectWalk.unevaluatedFileConstraintViolations(): List<Violation> =
    unevaluatedFileConstraints.map {
        uncheckedFileConstraintOf(it, UncheckedFileConstraintReason.NotEvaluated, cause = null)
    }
