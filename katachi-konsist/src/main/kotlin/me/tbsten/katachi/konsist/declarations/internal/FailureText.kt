package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason

// A failure's `declaration` is what the baseline keys it by, so it never holds a line number.

private const val NOT_ALLOWED: String = "Not allowed"
private const val MISSING: String = "Missing"

private val NOTHING_WRITTEN_NOTE: FileConstraintReason = FileConstraintReason(
    "Note",
    "ktFileContent { } wrote no line for this file, so nothing is allowed in it " +
        "(a condition in the block, such as if (fileName ...), may have left it out)",
)

internal fun failuresOf(findings: List<KtFileFinding>, path: String, rules: FileRules): List<FileConstraintFailure> {
    val opened = mutableListOf<OpeningKey>()
    return findings.mapIndexed { index, finding ->
        val key = openingKeyOf(finding, rules)
        val opening = key == null || opened.none { it.sameAs(key) }
        if (key != null && opening) opened += key
        val failure = failureOf(finding, path, rules, opening)
        if (index == 0 && rules.isEmpty) failure.copy(reasons = failure.reasons + NOTHING_WRITTEN_NOTE) else failure
    }
}

private class OpeningKey(val lines: Any, val containers: List<String>) {
    fun sameAs(other: OpeningKey): Boolean = lines === other.lines && containers == other.containers
}

private fun openingKeyOf(finding: KtFileFinding, rules: FileRules): OpeningKey? = when (finding) {
    is UnexpectedDeclaration -> OpeningKey(finding.list.rules, finding.list.containers.map { it.name })
    is UnexpectedImport -> OpeningKey(rules.imports, emptyList())
    else -> null
}

private fun failureOf(finding: KtFileFinding, path: String, rules: FileRules, opening: Boolean): FileConstraintFailure = when (finding) {
    is UnexpectedDeclaration -> unexpectedDeclarationFailure(finding, path, opening)
    is ProhibitedDeclaration -> prohibitedDeclarationFailure(finding, path)
    is MismatchedDeclaration -> mismatchedDeclarationFailure(finding, path)
    is MissingDeclaration -> missingDeclarationFailure(finding, path)
    is UnexpectedImport -> unexpectedImportFailure(finding, path, rules.declaredAt, nothingWritten = rules.imports.isEmpty(), opening)
    is ProhibitedImport -> prohibitedImportFailure(finding, path)
    is UnexpectedPackage -> unexpectedPackageFailure(finding, path, rules.declaredAt)
    is ProhibitedPackage -> prohibitedPackageFailure(finding, path)
}

private fun unexpectedDeclarationFailure(finding: UnexpectedDeclaration, path: String, opening: Boolean): FileConstraintFailure {
    val (fact, list) = finding.fact to finding.list
    val site = list.rules.declaredAt
    val container = list.container
    val fixes = buildList {
        if (opening) {
            nothingWrittenFix(list)?.let(::add)
            if (list.rules.allowsOnlyByNot()) add(onlyNotLinesFix("declarations", site, "allow(anythingElse)"))
        }
        // Renaming is only offered for the file's own declarations: a member is more often in the
        // wrong place than misnamed, and the remaining fixes say so. A name that is a pattern
        // (`*Action`, `{Alpha,Beta}`) is no name to rename to.
        if (container == null) {
            finding.unclaimed.singleOrNull()
                ?.takeIf { rule -> !rule.name.hasWildcard && rule.shapes.any { it.kind == fact.kind } }
                ?.let { add(FileConstraintFix("Rename it to ${it.name.written}")) }
        }
        list.rules.anythingElse.firstOrNull { it.allowsOnlyVisibilities() }?.let { rule ->
            val visibility = rule.alternatives.first().modifiers.single().keyword
            add(FileConstraintFix("Make it $visibility: ${renderAnythingElse(rule)} is allowed here"))
        }
        // A property of the primary constructor cannot be moved out of its class.
        when {
            container == null -> add(FileConstraintFix("Move it to a file of another role"))
            !fact.inConstructor -> add(FileConstraintFix("Move it out of ${renderContainer(container)}"))
        }
        add(
            when {
                container == null -> allowItFix(site, allowLineOf(fact))
                // One line to paste, so that two members of a class line without { } go into one { }.
                list.rules.withoutBody -> FileConstraintFix(
                    "Allow it in the { } of the line at $site, giving the line one if it has none:",
                    code = listOf(allowLineOf(fact)),
                )
                else -> FileConstraintFix("Allow it in the { } of the line at $site:", code = listOf(allowLineOf(fact)))
            },
        )
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
        howToFix = extraModifierFixes(rule, fact) + listOf(
            FileConstraintFix("Change the declaration to: ${renderRuleAsDeclaration(rule)}"),
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
            notRequiredFix(rule),
        ),
        ruleDeclaredAt = rule.declaredAt,
    )
}

private fun notRequiredFix(rule: DeclarationRule): FileConstraintFix =
    FileConstraintFix("If some files do not need it, write allow(...) in place of required(...) at ${rule.declaredAt}")

private fun foundFixes(found: DeclarationFact, rule: DeclarationRule): List<FileConstraintFix> = extraModifierFixes(rule, found) + listOf(
    FileConstraintFix("Change the declaration" + (found.line?.let { " at line $it" } ?: "") + " to: ${renderRuleAsDeclaration(rule)}"),
    FileConstraintFix("Or change the line at ${rule.declaredAt} if the declaration is right"),
    notRequiredFix(rule),
)

// A fun interface's `fun` is a kind of the vocabulary, not a modifier value, so it cannot be `.optional()`:
// the line is told to write `fun`, `interface` instead.
private fun extraModifierFixes(rule: DeclarationRule, fact: DeclarationFact): List<FileConstraintFix> {
    val extra = extraModifiersOf(closestShapeOf(rule, fact).modifiers, fact).takeIf { it.isNotEmpty() } ?: return emptyList()
    val optional = extra.filter { it != ModifierName.Fun }
    return listOfNotNull(
        optional.takeIf { it.isNotEmpty() }?.let { modifiers ->
            FileConstraintFix("Add ${modifiers.joinToString(", ") { "${it.keyword}.optional()" }} to the line at ${rule.declaredAt}")
        },
        FileConstraintFix(
            "Write `fun`, `interface` in the line at ${rule.declaredAt} (two lines if an interface without fun is allowed too)",
        ).takeIf { ModifierName.Fun in extra },
        FileConstraintFix("Remove ${extra.joinToString(", ") { it.keyword }} from the declaration"),
    )
}

private fun allowLineOf(fact: DeclarationFact): String {
    val modifiers = fact.modifiers.filter { it != ModifierName.Fun }.sortedBy { it.ordinal }.map { it.keyword }
    val kinds = listOfNotNull("`fun`".takeIf { ModifierName.Fun in fact.modifiers }, "`${fact.kind.keyword}`")
    return "allow(${(modifiers + kinds + namePatternLiteral(fact.name)).joinToString(", ")})"
}

private fun removeImportFixes(site: DeclarationSite, code: String, allowing: Boolean): List<FileConstraintFix> = listOf(
    FileConstraintFix("Remove the import and receive what it needs from outside"),
    if (allowing) allowItFix(site, code) else exceptFixWithCode("If this import is fine, exclude it from the prohibited line at $site:", code),
)

private fun nothingWrittenFix(list: DeclarationListContext): FileConstraintFix? {
    val rules = list.rules
    if (rules.entries.isNotEmpty()) return null
    val site = rules.declaredAt
    val container = list.container
    return when {
        container == null -> FileConstraintFix(
            "No declaration line is written, so every declaration is rejected. To allow any, add at $site:",
            code = listOf("allow(anythingElse)"),
        )
        rules.withoutBody -> FileConstraintFix(
            "The line at $site has no { }, so ${renderContainer(container)} may have no members. To allow any, give it:",
            code = listOf("{ allow(anythingElse) }"),
        )
        else -> FileConstraintFix(
            "No line is written in the { } at $site, so every member is rejected. To allow any, add there:",
            code = listOf("allow(anythingElse)"),
        )
    }
}

private fun unexpectedImportFailure(
    finding: UnexpectedImport,
    path: String,
    site: DeclarationSite,
    nothingWritten: Boolean,
    opening: Boolean,
): FileConstraintFailure {
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
        howToFix = listOfNotNull(
            when {
                !opening -> null
                nothingWritten -> FileConstraintFix(
                    "No import line is written, so every import is rejected. To allow any import, add at $site:",
                    code = listOf("`import`(\"**\")"),
                )
                finding.allowed.isEmpty() -> onlyNotLinesFix("imports", site, "`import`(\"**\")")
                else -> null
            },
        ) + removeImportFixes(site, "`import`(${kotlinString(import)})", allowing = true),
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

private fun unexpectedPackageFailure(finding: UnexpectedPackage, path: String, blockSite: DeclarationSite): FileConstraintFailure {
    val packageName = finding.packageName
    if (finding.firstRule == null && packageName != null) {
        return undeclaredPackageFailure(packageName, finding.line, path, blockSite, finding.directoryPackage)
    }
    val ruleSite = finding.firstRule?.declaredAt ?: blockSite
    val directoryPackage = finding.directoryPackage?.takeIf { it.isNotEmpty() }
    val hasMatchingDirectory = finding.allowed.any { it.target == PackageTarget.MatchingDirectory }
    return FileConstraintFailure(
        file = path,
        declaration = packageName?.let { "package $it" } ?: "package (none)",
        line = finding.line,
        reasons = listOf(FileConstraintReason(NOT_ALLOWED, packageName?.let { "package $it" } ?: "the file declares no package")),
        allowed = FileConstraintAllowedList("Allowed here", finding.allowed.map { renderPackage(it, directoryPackage) }),
        howToFix = buildList {
            if (finding.allowed.isEmpty()) add(onlyNotLinesFix("packages", ruleSite, "`package`(\"**\")"))
            add(FileConstraintFix(if (packageName == null) "Declare a package allowed above" else "Change the package to one allowed above"))
            if (hasMatchingDirectory && packageName != null) add(FileConstraintFix("Or move the file to the directory its package names"))
            if (!hasMatchingDirectory && packageName != null && packageName == finding.directoryPackage) add(matchingDirectoryFix(ruleSite))
        },
        ruleDeclaredAt = ruleSite,
    )
}

private fun undeclaredPackageFailure(
    packageName: String,
    line: Int?,
    path: String,
    site: DeclarationSite,
    directoryPackage: String?,
): FileConstraintFailure = FileConstraintFailure(
    file = path,
    declaration = "package $packageName",
    line = line,
    reasons = listOf(FileConstraintReason(NOT_ALLOWED, "package $packageName")),
    allowed = FileConstraintAllowedList("Allowed here", emptyList()),
    howToFix = listOfNotNull(
        FileConstraintFix(
            "No package line is written, so no package may be declared. To allow any package, add at $site:",
            code = listOf("`package`(\"**\")"),
        ),
        allowItFix(site, "`package`(${kotlinString(packageName)})"),
        matchingDirectoryFix(site).takeIf { packageName == directoryPackage },
    ),
    ruleDeclaredAt = site,
)

private fun matchingDirectoryFix(site: DeclarationSite): FileConstraintFix =
    FileConstraintFix("Or allow the package each file's directory names, at $site:", code = listOf("`package`(matchingDirectory)"))

private fun prohibitedPackageFailure(finding: ProhibitedPackage, path: String): FileConstraintFailure {
    val packageName = finding.packageName
    return FileConstraintFailure(
        file = path,
        declaration = packageName?.let { "package $it" } ?: "package (none)",
        line = finding.line,
        reasons = listOf(
            FileConstraintReason(NOT_ALLOWED, packageName?.let { "package $it" } ?: "the file declares no package"),
            FileConstraintReason("Prohibited by", renderPackage(finding.rule)),
        ),
        howToFix = if (packageName == null) {
            listOf(FileConstraintFix("Declare a package the prohibited line does not match"))
        } else {
            listOf(
                FileConstraintFix("Change the package"),
                exceptFix("If this package is fine, exclude it from the prohibited line at ${finding.rule.declaredAt}:", packageName),
            )
        },
        ruleDeclaredAt = finding.rule.declaredAt,
    )
}

private fun headingOf(list: DeclarationListContext): String =
    list.container?.let { "Allowed members of ${renderContainer(it)}" } ?: "Allowed here"

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

private fun inContainer(list: DeclarationListContext): String = list.container?.let { " in ${renderContainer(it)}" }.orEmpty()

private fun AnythingElseRule.allowsOnlyVisibilities(): Boolean = alternatives.all { set ->
    set.annotations.isEmpty() && set.modifiers.size == 1 && set.modifiers.single() in ModifierName.VISIBILITIES
}
