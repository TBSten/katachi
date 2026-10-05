package me.tbsten.katachi.check.internal

import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.processor.internal.ProjectWalk

/**
 * What [declared] is handed when it is evaluated against [files], the files this run's walk found
 * for it.
 *
 * `FileConstraintCheck` and the definition task (`DescribeDefinition`, which explains the
 * declarations of each file the way the check judges them) both build the subject here, so the two
 * see the same files, the same capture values and the same per-run memo.
 */
internal fun fileConstraintSubjectOf(
    walk: ProjectWalk,
    declared: DeclaredFileConstraint,
    files: List<String> = walk.filesUnder(declared),
): FileConstraintSubject = FileConstraintSubject(
    role = declared.role,
    name = declared.name,
    declaredAt = declared.declaredAt,
    paths = declared.paths,
    projectRoot = walk.projectRootPath,
    files = files,
    shared = walk.scratch(),
    captureNames = declared.coverage.captureNames,
    captureValues = declared.coverage::captureValuesOf,
)
