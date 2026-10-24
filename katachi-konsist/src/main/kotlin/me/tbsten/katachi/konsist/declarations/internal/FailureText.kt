package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason

/*
 * The findings of one file worded as report blocks. Every line katachi writes here is ASCII;
 * only the names the user wrote and the file holds may be anything else.
 *
 * The `declaration` of a failure is what the baseline keys it by, so it never holds a line
 * number: `GetUserUseCaseImpl`, `HomeViewModel.isLoading`, `import android.content.Context`,
 * `package com.example.feature.homex`.
 */

private const val NOT_ALLOWED: String = "Not allowed"
private const val MISSING: String = "Missing"

/** [finding] about the file at [path] (project relative), whose rules are [rules]. */
internal fun failureOf(finding: KtFileFinding, path: String, rules: FileRules): FileConstraintFailure = when (finding) {
    is UnexpectedDeclaration -> unexpectedDeclarationFailure(finding, path)
    is ProhibitedDeclaration -> prohibitedDeclarationFailure(finding, path)
    is MismatchedDeclaration -> mismatchedDeclarationFailure(finding, path)
    is MissingDeclaration -> missingDeclarationFailure(finding, path)
    is UnexpectedImport -> unexpectedImportFailure(finding, path, rules.declaredAt)
    is ProhibitedImport -> prohibitedImportFailure(finding, path)
    is MissingImport -> missingImportFailure(finding, path)
    is UnexpectedPackage -> unexpectedPackageFailure(finding, path)
    is ProhibitedPackage -> prohibitedPackageFailure(finding, path)
}

private fun unexpectedDeclarationFailure(finding: UnexpectedDeclaration, path: String): FileConstraintFailure {
    val (fact, list) = finding.fact to finding.list
    val site = list.rules.declaredAt
    val container = list.container
    val fixes = buildList {
        if (list.rules.allowsOnlyByNot()) add(onlyNotLinesFix("declarations", site, "allow(anythingElse)"))
        // Renaming is only offered for the file's own declarations: a member is more often in the
        // wrong place than misnamed, and the remaining fixes say so.
        if (container == null) {
            finding.unclaimed.singleOrNull()?.takeIf { rule -> rule.shapes.any { it.kind == fact.kind } }
                ?.let { add(FileConstraintFix("Rename it to ${it.name.written}")) }
        }
        list.rules.anythingElse.firstOrNull { it.allowsOnlyVisibilities() }?.let { rule ->
            val visibility = rule.alternatives.first().modifiers.single().keyword
            add(FileConstraintFix("Make it $visibility: ${renderAnythingElse(rule)} is allowed here"))
        }
        add(
            FileConstraintFix(
                if (container == null) "Move it to a file of another role" else "Move it out of ${renderContainer(container)}",
            ),
        )
        add(allowItFix(site, "allow(`${fact.kind.keyword}`, ${namePatternLiteral(fact.name)}).optional()"))
    }
    return FileConstraintFailure(
        file = path,
        declaration = list.qualified(fact.name),
        line = fact.line,
        reasons = listOf(FileConstraintReason(NOT_ALLOWED, renderFact(fact) + inContainer(list))),
        allowed = FileConstraintAllowedList(headingOf(list), allowedEntriesOf(list)),
        howToFix = fixes,
        ruleDeclaredAt = site,
    )
}

private fun prohibitedDeclarationFailure(finding: ProhibitedDeclaration, path: String): FileConstraintFailure {
    val (fact, rule) = finding.fact to finding.rule
    return FileConstraintFailure(
        file = path,
        declaration = finding.list.qualified(fact.name),
        line = fact.line,
        reasons = listOf(
            FileConstraintReason(NOT_ALLOWED, renderFact(fact) + inContainer(finding.list)),
            FileConstraintReason("Prohibited by", renderRule(rule)),
        ),
        howToFix = listOf(
            FileConstraintFix("Remove it or rename it"),
            exceptFixWithCode(
                "If it is fine here, exclude it from the prohibited line at ${rule.declaredAt}:",
                ".except(${namePatternLiteral(fact.name)})",
            ),
        ),
        ruleDeclaredAt = rule.declaredAt,
    )
}

private fun mismatchedDeclarationFailure(finding: MismatchedDeclaration, path: String): FileConstraintFailure {
    val (fact, rule) = finding.fact to finding.rule
    val expected = renderRule(rule)
    return FileConstraintFailure(
        file = path,
        declaration = finding.list.qualified(fact.name),
        line = fact.line,
        reasons = listOf(
            FileConstraintReason(NOT_ALLOWED, renderFact(fact) + inContainer(finding.list)),
            FileConstraintReason("Expected", expected),
            FileConstraintReason("Differs in", finding.differences.joinToString("; ")),
        ),
        howToFix = listOf(
            FileConstraintFix("Change the declaration to: $expected"),
            FileConstraintFix("Or change the line at ${rule.declaredAt} if the declaration is right"),
        ),
        ruleDeclaredAt = rule.declaredAt,
    )
}

private fun missingDeclarationFailure(finding: MissingDeclaration, path: String): FileConstraintFailure {
    val (rule, list) = finding.rule to finding.list
    val container = list.container
    return FileConstraintFailure(
        file = path,
        declaration = list.qualified(rule.name.written),
        line = null,
        reasons = buildList {
            add(FileConstraintReason(MISSING, renderRule(rule) + inContainer(list)))
            finding.found?.let { found ->
                add(FileConstraintReason("Found", renderFact(found) + (found.line?.let { " (line $it)" } ?: "")))
            }
        },
        howToFix = finding.found?.let { found -> foundFixes(found, rule) } ?: listOf(
            FileConstraintFix(if (container == null) "Declare it in this file" else "Declare it in ${renderContainer(container)}"),
            FileConstraintFix("If some files do not need it, make it optional at ${rule.declaredAt}"),
        ),
        ruleDeclaredAt = rule.declaredAt,
    )
}

/**
 * The fixes of a missing line whose name a declaration of another shape has: the declaration is
 * there, so it is its shape, or the line, that needs changing.
 */
private fun foundFixes(found: DeclarationFact, rule: DeclarationRule): List<FileConstraintFix> = listOf(
    FileConstraintFix("Change the declaration" + (found.line?.let { " at line $it" } ?: "") + " to: ${renderRule(rule)}"),
    FileConstraintFix("Or change the line at ${rule.declaredAt} if the declaration is right"),
    FileConstraintFix("If some files do not need it, make it optional at ${rule.declaredAt}"),
)

/** The fixes of an import that should not be there: remove it, or allow it at [site] with [code]. */
private fun removeImportFixes(site: DeclarationSite, code: String, allowing: Boolean): List<FileConstraintFix> = listOf(
    FileConstraintFix("Remove the import and receive what it needs from outside"),
    if (allowing) allowItFix(site, code) else exceptFixWithCode("If this import is fine, exclude it from the prohibited line at $site:", code),
)

private fun unexpectedImportFailure(finding: UnexpectedImport, path: String, site: DeclarationSite): FileConstraintFailure {
    val import = finding.import.path
    val reason = if (finding.allowed.isEmpty()) {
        "ktFileContent { } allows no import here"
    } else {
        "not in the allowed imports (${finding.allowed.joinToString(", ") { it.path.written }})"
    }
    return FileConstraintFailure(
        file = path,
        declaration = "import $import",
        line = finding.import.line,
        reasons = listOf(FileConstraintReason(NOT_ALLOWED, reason)),
        // An import allowed in every file of the role would be required in each, hence .optional().
        howToFix = listOfNotNull(
            onlyNotLinesFix("imports", site, "`import`(\"**\")").takeIf { finding.allowed.isEmpty() },
        ) + removeImportFixes(site, "`import`(${kotlinString(import)}).optional()", allowing = true),
        ruleDeclaredAt = site,
    )
}

private fun prohibitedImportFailure(finding: ProhibitedImport, path: String): FileConstraintFailure {
    val import = finding.import.path
    return FileConstraintFailure(
        file = path,
        declaration = "import $import",
        line = finding.import.line,
        reasons = listOf(
            FileConstraintReason(NOT_ALLOWED, "import $import"),
            FileConstraintReason("Prohibited by", renderImport(finding.rule)),
        ),
        howToFix = removeImportFixes(finding.rule.declaredAt, ".except(${kotlinString(import)})", allowing = false),
        ruleDeclaredAt = finding.rule.declaredAt,
    )
}

private fun missingImportFailure(finding: MissingImport, path: String): FileConstraintFailure {
    val rule = finding.rule
    return FileConstraintFailure(
        file = path,
        declaration = "import ${rule.path.written}",
        line = null,
        reasons = listOf(FileConstraintReason(MISSING, renderImport(rule))),
        howToFix = listOf(
            FileConstraintFix("Add the import"),
            FileConstraintFix("If some files do not need it, make it optional at ${rule.declaredAt}"),
        ),
        ruleDeclaredAt = rule.declaredAt,
    )
}

private fun unexpectedPackageFailure(finding: UnexpectedPackage, path: String): FileConstraintFailure {
    val packageName = finding.packageName
    val directoryPackage = finding.directoryPackage?.takeIf { it.isNotEmpty() }
    val hasMatchingDirectory = finding.allowed.any { it.target == PackageTarget.MatchingDirectory }
    return FileConstraintFailure(
        file = path,
        declaration = packageName?.let { "package $it" } ?: "package (none)",
        line = finding.line,
        reasons = listOf(FileConstraintReason(NOT_ALLOWED, packageName?.let { "package $it" } ?: "the file declares no package")),
        allowed = FileConstraintAllowedList("Allowed here", finding.allowed.map { renderPackage(it, directoryPackage) }),
        howToFix = buildList {
            if (finding.allowed.isEmpty()) add(onlyNotLinesFix("packages", finding.firstRule.declaredAt, "`package`(\"**\")"))
            add(FileConstraintFix(if (packageName == null) "Declare a package allowed above" else "Change the package to one allowed above"))
            if (hasMatchingDirectory && packageName != null) add(FileConstraintFix("Or move the file to the directory its package names"))
        },
        ruleDeclaredAt = finding.firstRule.declaredAt,
    )
}

private fun prohibitedPackageFailure(finding: ProhibitedPackage, path: String): FileConstraintFailure = FileConstraintFailure(
    file = path,
    declaration = "package ${finding.packageName}",
    line = finding.line,
    reasons = listOf(
        FileConstraintReason(NOT_ALLOWED, "package ${finding.packageName}"),
        FileConstraintReason("Prohibited by", renderPackage(finding.rule)),
    ),
    howToFix = listOf(
        FileConstraintFix("Change the package"),
        exceptFix("If this package is fine, exclude it from the prohibited line at ${finding.rule.declaredAt}:", finding.packageName),
    ),
    ruleDeclaredAt = finding.rule.declaredAt,
)

/** `Allowed here` for the file's declarations, `Allowed members of class X` for a class's. */
private fun headingOf(list: DeclarationListContext): String =
    list.container?.let { "Allowed members of ${renderContainer(it)}" } ?: "Allowed here"

/**
 * The allowed lines and `anythingElse` lines of the list, in the order written, a required line no
 * declaration matches marked `(missing)`. The `not.` lines are left out: they allow nothing.
 */
private fun allowedEntriesOf(list: DeclarationListContext): List<String> = list.rules.entries.mapNotNull { entry ->
    when (entry) {
        is DeclarationRule -> when {
            entry.prohibited -> null
            list.isMissing(entry) -> "${renderRule(entry)}  (missing)"
            else -> renderRule(entry)
        }
        is AnythingElseRule -> renderAnythingElse(entry)
    }
}

/** ` in class HomeViewModel` for a member, nothing for a declaration of the file. */
private fun inContainer(list: DeclarationListContext): String = list.container?.let { " in ${renderContainer(it)}" }.orEmpty()

/** An `anythingElse` line that asks for one visibility and nothing more, in every alternative. */
private fun AnythingElseRule.allowsOnlyVisibilities(): Boolean = alternatives.all { set ->
    set.annotations.isEmpty() && set.modifiers.size == 1 && set.modifiers.single() in ModifierName.VISIBILITIES
}
