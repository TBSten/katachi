package me.tbsten.katachi.konture.internal

import io.github.baole.konture.core.model.Subject
import io.github.baole.konture.core.model.Violation
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.konture.KatachiKontureUnlocatableViolationException

// A data class so that one declaration rejected twice collapses to one entry.
internal data class KontureViolationSite(
    val file: String,
    val declaration: String?,
    val line: Int?,
)

// What a violation carries depends on the builder, so the caller passes `kind`: `files` is named after the file at line 1 (a convention; a location equal to the subject's means the whole file),
// `classes` use `simpleName`, `functions` and `properties` use the last segment of `pkg[.Class].name`.
internal fun siteOf(
    kind: SuiteKind,
    violation: Violation,
    covered: KontureCoveredFiles,
): KontureViolationSite {
    val subject = violation.subject
    val location = violation.sourceLocation ?: subject.location
        ?: throw KatachiKontureUnlocatableViolationException(
            ruleId = violation.ruleId,
            subject = subject.name,
            filePath = null,
        )
    val relative = covered.relativeOf(location.filePath)
        ?: throw KatachiKontureUnlocatableViolationException(
            ruleId = violation.ruleId,
            subject = subject.name,
            filePath = location.filePath,
        )
    val declaration = when (kind) {
        SuiteKind.Files -> null
        SuiteKind.Classes -> when (subject) {
            is Subject.ClassSubject -> subject.simpleName
            else -> subject.name.substringAfterLast('.')
        }
        SuiteKind.Functions, SuiteKind.Properties -> subject.name.substringAfterLast('.')
    }
    // Konture writes `-1` for a line it does not know. A files violation at the subject's own
    // location is about the whole file; its `line = 1` is Konture's placeholder, not a fact.
    val line = when {
        kind == SuiteKind.Files && violation.sourceLocation == subject.location -> null
        else -> location.line?.takeIf { it > 0 }
    }
    return KontureViolationSite(file = relative, declaration = declaration, line = line)
}

internal fun KontureViolationSite.toFileConstraintFailure(): FileConstraintFailure =
    FileConstraintFailure(file = file, declaration = declaration, line = line)
