package me.tbsten.katachi.konture.internal

import io.github.baole.konture.core.model.Subject
import io.github.baole.konture.core.model.Violation
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.konture.KatachiKontureUnlocatableViolationException

/**
 * One place a block rejected: enough to open a report block on, and nothing more.
 *
 * A `data class` so that the same declaration rejected twice — by two rules in one block, or by
 * two assertions of one rule — collapses to one entry in the set that holds these. Two different
 * declarations of one file stay two.
 */
internal data class KontureViolationSite(
    /** Project relative, `/` separated, spelled as in `FileConstraintSubject.files`. */
    val file: String,
    /** The simple name of the rejected declaration, or `null` for a whole file. */
    val declaration: String?,
    /** 1-based, or `null` when the violation is about the whole file or Konture could not place it. */
    val line: Int?,
)

/**
 * Where [violation] is, written the way katachi's report wants it.
 *
 * What a Konture violation carries depends on the builder that made it, and the builder is
 * known only to the caller, hence [kind]:
 *
 * - `files`: a `CustomSubject` named after the file at `line = 1`, the line being a convention
 *   rather than a fact. A violation that points at the subject's own location is about the whole
 *   file, so it carries no declaration and no line; one that points elsewhere keeps its line.
 * - `classes`: a `ClassSubject`, whose `simpleName` is the declaration.
 * - `functions` and `properties`: a subject named `pkg[.Class].name`, of which the last segment
 *   is the declaration.
 *
 * The path is made project relative by [covered], which also accepts another spelling of a
 * covered file; a violation on a file the constraint never asked about has no block to appear
 * in, and is a katachi bug.
 *
 * @throws KatachiKontureUnlocatableViolationException when the violation carries no location,
 *   or one that is not a covered file.
 */
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

/** One rejection, with its path written the way every other path in a report is written. */
internal fun KontureViolationSite.toFileConstraintFailure(): FileConstraintFailure =
    FileConstraintFailure(file = file, declaration = declaration, line = line)
