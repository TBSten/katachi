package me.tbsten.katachi.check.internal

import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.processor.internal.ProjectWalk

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
