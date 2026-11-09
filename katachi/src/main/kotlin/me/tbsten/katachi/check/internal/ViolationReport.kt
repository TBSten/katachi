package me.tbsten.katachi.check.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.*
import me.tbsten.katachi.check.DEFAULT_MAX_VIOLATIONS
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.displayExistingPath
import me.tbsten.katachi.internal.displayPath

internal const val STEP: String = "  "

/**
 * The violations, written out for the standard output of a test.
 *
 * ASCII only, one block per violation, every path a `file:///...` URI, and the counts on the
 * first line. The text has three shapes:
 * - **Any error present** — the usual failure report (summary, error blocks, the error
 *   truncation line, the sentences saying what could not be checked), and — if there are any —
 *   a Warning section appended after it.
 * - **No errors, but warnings** — the Warning section alone. Nothing failed, so there is no
 *   failure summary to hang it under.
 * - **Neither** — an empty string.
 *
 *
 * @param maxViolations how many error and warning blocks this writes out, together. When both
 *   severities are present, warnings take at least one block and at most a quarter; the summary
 *   counts every error either way.
 * @param projectRoot what the violations' relative paths are resolved against to print them as
 *   `file:///...` URIs. `null` prints them as the violations carry them, relative to the root.
 */
internal fun List<Violation>.report(
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
    projectRoot: FsPath? = null,
): String {
    val errors = filter { it.severity == Severity.Error }
    val warnings = filter { it.severity == Severity.Warning }
    if (errors.isEmpty() && warnings.isEmpty()) return ""

    val budget = maxViolations.coerceAtLeast(0)
    // Warnings draw from the same budget as errors, not a budget of their own: a report may
    // never spell out more than `maxViolations` blocks in total. When one severity is absent,
    // the other gets the whole budget, exactly as any single-severity list always has. When
    // both are present, warnings take a slice capped at a quarter of the budget — never fewer
    // than one block, so a report full of errors cannot crowd every warning out, and never
    // more than there are warnings to show — and errors take the rest.
    val warningBudget = when {
        budget <= 0 -> 0
        errors.isEmpty() -> minOf(warnings.size, budget)
        else -> minOf(warnings.size, maxOf(1, budget / 4))
    }
    val errorBudget = budget - warningBudget
    val shownErrors = shownIndicesOf(errors, errorBudget).map { errors[it] }
    val shownWarnings = shownIndicesOf(warnings, warningBudget).map { warnings[it] }

    return buildString {
        if (errors.isNotEmpty()) {
            append(summaryLine(errors, warnings.size))
            for (violation in shownErrors) {
                append("\n\n")
                append(blockOf(violation, projectRoot?.value).joinToString("\n"))
            }
            truncationLine(errors, shownErrors, noun = "", withBreakdown = true)?.let {
                append("\n\n$it\n$STEP$TRUNCATION_HINT")
            }
            // After the truncation line, and outside it: these are the only sentences saying
            // the result is partial, and a reader who does not see them is entitled to believe
            // every path, constraint and check was looked at. Cutting them away with the
            // blocks would make the report lie.
            for (line in uncheckedLines(errors)) append("\n\n$line")
        }
        if (warnings.isNotEmpty()) {
            if (errors.isNotEmpty()) append("\n\n")
            append(warningHeading(warnings.size))
            for (violation in shownWarnings) {
                append("\n\n")
                append(blockOf(violation, projectRoot?.value).joinToString("\n"))
            }
            truncationLine(warnings, shownWarnings, noun = " warnings", withBreakdown = false)
                ?.let { append("\n\n$it\n$STEP$TRUNCATION_HINT") }
        }
    }
}

internal fun List<Violation>.reportWithTrailer(
    maxViolations: Int,
    projectRoot: FsPath?,
    trailer: List<String>,
): String {
    val report = report(maxViolations, projectRoot)
    if (trailer.isEmpty()) return report
    return if (report.isEmpty()) trailer.joinToString("\n") else report + "\n\n" + trailer.joinToString("\n")
}

private fun shownIndicesOf(violations: List<Violation>, max: Int): List<Int> {
    val indicesByKind = violations.indices.groupBy { violations[it].kind }
    val quota = ViolationKind.entries.associateWith { 0 }.toMutableMap()
    var budget = max

    for (kind in ViolationKind.entries) {
        if (budget <= 0) break
        if (indicesByKind[kind].isNullOrEmpty()) continue
        quota[kind] = 1
        budget--
    }

    while (budget > 0) {
        var grew = false
        for (kind in ViolationKind.entries) {
            if (budget <= 0) break
            val available = indicesByKind[kind]?.size ?: 0
            val current = quota.getValue(kind)
            if (current < available) {
                quota[kind] = current + 1
                budget--
                grew = true
            }
        }
        // Every present kind is fully shown; a larger max cannot add more.
        if (!grew) break
    }

    return ViolationKind.entries
        .flatMap { kind -> indicesByKind[kind].orEmpty().take(quota.getValue(kind)) }
        .sorted()
}

// The warning count stays outside the per-kind breakdown, which explains why the run failed.
private fun summaryLine(errors: List<Violation>, warningCount: Int): String {
    val counted = ViolationKind.entries
        .map { kind -> kind to errors.count { it.kind == kind } }
        .filter { (_, count) -> count > 0 }
        .joinToString(", ") { (kind, count) -> "$kind: $count" }
    val total = "${errors.size} ${if (errors.size == 1) "violation" else "violations"}"
    val base = if (counted.isEmpty()) "Katachi check failed: $total" else "Katachi check failed: $total ($counted)"
    return if (warningCount > 0) {
        "$base, $warningCount ${if (warningCount == 1) "warning" else "warnings"}"
    } else {
        base
    }
}

// The second sentence stays the same for any count, on purpose.
private fun warningHeading(count: Int): String {
    val noun = if (count == 1) "warning" else "warnings"
    return "Katachi check found $count $noun. Warnings never fail the check."
}

private fun truncationLine(
    all: List<Violation>,
    shown: List<Violation>,
    noun: String,
    withBreakdown: Boolean
): String? {
    val hiddenByKind = ViolationKind.entries
        .associateWith { kind -> all.count { it.kind == kind } - shown.count { it.kind == kind } }
        .filterValues { it > 0 }
    val hidden = hiddenByKind.values.sum()
    if (hidden <= 0) return null
    // A breakdown only earns its place when more than one kind is hiding something; with a
    // single kind hidden it would just repeat the count already on this line.
    val breakdown = if (withBreakdown && hiddenByKind.size > 1) {
        ": " + hiddenByKind.entries.joinToString(", ") { "${it.key} ${it.value}" }
    } else {
        ""
    }
    return "Showing first ${shown.size}$noun ($hidden more$breakdown)"
}

private fun uncheckedLines(violations: List<Violation>): List<String> = buildList {
    val files = violations.count { it is UncheckedFile }
    val directories = violations.count { it is UncheckedDirectory }
    val paths = files + directories
    if (paths > 0) {
        val noun = when {
            directories == 0 -> if (files == 1) "file" else "files"
            files == 0 -> if (directories == 1) "directory" else "directories"
            else -> "paths"
        }
        add("$paths $noun could not be checked.")
    }
    val constraints = violations.count { it is UncheckedFileConstraint }
    if (constraints > 0) {
        add("$constraints ${if (constraints == 1) "constraint" else "constraints"} could not be evaluated.")
    }
    val checks = violations.count { it is UncheckedCheck }
    if (checks > 0) add("$checks ${if (checks == 1) "check" else "checks"} could not be run.")
}

internal fun blockOf(violation: Violation, root: String?): List<String> = when (violation) {
    is UnexpectedFile -> unexpectedFileBlock(violation, root)
    is UnexpectedDirectory -> unexpectedDirectoryBlock(violation, root)
    is CaptureMismatch -> captureMismatchBlock(violation, root)
    is MissingFile -> missingFileBlock(violation, root)
    is UncheckedFile -> uncheckedFileBlock(violation, root)
    is UncheckedDirectory -> uncheckedDirectoryBlock(violation, root)
    is UncheckedCheck -> uncheckedCheckBlock(violation, root)
    is UnsatisfiedFileConstraint -> unsatisfiedFileConstraintBlock(violation, root)
    is UncheckedFileConstraint -> uncheckedFileConstraintBlock(violation, root)
    is AmbiguousLayout -> ambiguousLayoutBlock(violation, root)
    is MissingDescription -> missingDescriptionBlock(violation, root)
    is StaleBaselineEntry -> staleBaselineEntryBlock(violation, root)
    // A violation from outside katachi. The block is the first line plus the values it states
    // about itself, and nothing else: katachi does not know what it means, so it writes no
    // sentence about it and never offers a way to fix it. Compile-time exhaustiveness is lost
    // the moment `Violation` stops being sealed; `ViolationBlockCoverageSpec` gets it back for
    // katachi's own violations by checking every one of them has a branch above this line.
    else -> foreignBlock(violation, root)
}

private fun unexpectedFileBlock(violation: UnexpectedFile, root: String?): List<String> = buildList {
    add("[${violation.label}] ${displayExistingPath(root, violation.path)}")
    add("${STEP}No role is defined for this file.")
    if (violation.nearby.isNotEmpty()) {
        // Inside the branch, not before it: with no nearby locations this and the blank line
        // before `How to fix:` would land back to back and open a two-line gap.
        add("")
        add("${STEP}Nearby locations:")
        val width = violation.nearby.maxOf { it.role.qualifiedName.length }
        for (location in violation.nearby) {
            val pattern = location.filePattern?.let { " ($it)" }.orEmpty()
            add("$STEP$STEP${location.role.qualifiedName.padEnd(width)} ${displayPath(root, location.directory)}/$pattern")
        }
    }
    add("")
    add("${STEP}How to fix:")
    if (violation.nearby.isNotEmpty()) {
        // The parentheses are only mentioned when a line has them: every location of an
        // `anyFile()` role takes any name, and there is nothing to match then.
        val named = violation.nearby.any { it.filePattern != null }
        add(
            "$STEP$STEP- Move it to one of the locations above" +
                if (named) ", with a name matching the pattern in parentheses" else "",
        )
    }
    add("$STEP$STEP- Delete it if it is not needed")
    add("$STEP$STEP- Add a new role for it:")
    val suggestion = violation.suggestedRole
        ?.let { roleSuggestionFor(violation.path, isDirectory = false, roleName = it) }
        ?: roleSuggestionFor(violation.path, isDirectory = false)
    addAll(fragment(suggestion))
}

private fun unexpectedDirectoryBlock(violation: UnexpectedDirectory, root: String?): List<String> = buildList {
    add("[${violation.label}] ${displayExistingPath(root, violation.path)}")
    add("${STEP}No role is defined for this directory. Nothing below it was checked.")
    add("")
    add("${STEP}How to fix:")
    add("$STEP$STEP- Delete it if it is not needed")
    add("$STEP$STEP- Declare what belongs in it in the layout of an existing role")
    add("$STEP$STEP- Add a new role for it:")
    addAll(fragment(roleSuggestionFor(violation.path, isDirectory = true)))
}

private fun captureMismatchBlock(violation: CaptureMismatch, root: String?): List<String> = buildList {
    val failed = violation.occurrences.filter { it.expected != null && it.expected != it.value }
    val failedNames = failed.map { it.name }.distinct()
    add("[${violation.label}] ${displayExistingPath(root, violation.path)}")
    add(
        "${STEP}The file matches a pattern of role ${violation.role.qualifiedName}, but the levels named by " +
            "${failedNames.joinToString(", ") { "capture(\"$it\")" }} do not agree.",
    )
    add("${STEP}Pattern: ${violation.pattern}")
    for ((name, plain) in violation.occurrences.filter { it.transform == null }.groupBy { it.name }) {
        add("$STEP$name: ${plain.joinToString(", ") { occurrenceText(it) }}")
    }
    for (derived in violation.occurrences.filter { it.transform != null }) {
        val source = violation.occurrences.firstOrNull { it.name == derived.name && it.transform == null && it.expected == null }
        val from = source?.let { ", from ${it.name} = \"${it.value}\"" }.orEmpty()
        add("$STEP${derived.name}.${derived.transform}: ${occurrenceText(derived, from)}")
    }
    add("${STEP}Declared at: ${violation.declaredAt}")
    add("")
    add("${STEP}How to fix:")
    val expectedPath = violation.expectedPath
    val held = failedNames.singleOrNull()?.let { name ->
        violation.occurrences.firstOrNull { it.name == name && it.transform == null && it.expected == null }?.value
    }
    val reads = if (held != null) "every level named ${failedNames.single()} reads \"$held\"" else "the levels named ${failedNames.joinToString(", ")} agree"
    if (expectedPath != null) {
        add("$STEP$STEP- Move it so that $reads:")
        add("$STEP$STEP$STEP$STEP${displayPath(root, expectedPath)}")
    } else {
        add("$STEP$STEP- Move it so that ${if (held != null) reads else "every level named ${failedNames.joinToString(", ")} reads the same value"}")
    }
    add("$STEP$STEP- Delete it if it is not needed")
    if (failed.any { it.transform == null }) {
        add("$STEP$STEP- If the two levels may differ, give them different names in the layout at ${violation.declaredAt}")
    }
    for (transform in failed.mapNotNull { it.transform }.distinct()) {
        add("$STEP$STEP- If the file name need not follow the directory, drop .$transform at ${violation.declaredAt}")
    }
}

private fun occurrenceText(occurrence: CaptureOccurrence, from: String = ""): String {
    val expected = occurrence.expected
    val held = if (expected != null && expected != occurrence.value) " (expected \"$expected\"$from)" else ""
    // 1-based, as `katachiTemplates` counts levels.
    return "\"${occurrence.value}\" at level ${occurrence.level + 1}$held"
}

private fun missingFileBlock(violation: MissingFile, root: String?): List<String> = listOf(
    "[${violation.label}] ${displayPath(root, violation.path)}",
    // Neutral on purpose: a new module fails this way once, whichever of the declaration and
    // the file is written first, and being told the definition is wrong would be misleading.
    "${STEP}No file has been created yet for role ${violation.role.qualifiedName}.",
    "${STEP}Declared at: ${violation.declaredAt}",
    "",
    "${STEP}How to fix:",
    "$STEP$STEP- If it is not implemented yet, this error is expected",
    "$STEP$STEP- If it is no longer needed, remove the declaration at ${violation.declaredAt}",
)

private fun uncheckedFileBlock(violation: UncheckedFile, root: String?): List<String> = listOf(
    "[${violation.label}] ${displayExistingPath(root, violation.path)}",
    "${STEP}Katachi failed while checking this file, so nothing is known about it.",
    *causeLines(violation.cause).toTypedArray(),
    "",
    "${STEP}How to fix:",
    "$STEP$STEP- Check that the file is readable, then run the check again",
    "$STEP$STEP- If it is, report this at https://github.com/TBSten/katachi/issues with the cause above",
)

private fun uncheckedDirectoryBlock(violation: UncheckedDirectory, root: String?): List<String> = listOf(
    "[${violation.label}] ${displayExistingPath(root, violation.path)}",
    when (violation.reason) {
        UncheckedDirectoryReason.NotWalked ->
            "${STEP}Katachi failed while checking this directory. Nothing below it was checked."

        UncheckedDirectoryReason.ModulesNotDiscovered ->
            "${STEP}Katachi failed while looking for modules here, so gradle() may have declared " +
                    "the build scripts of fewer modules than the project has."
    },
    *causeLines(violation.cause).toTypedArray(),
    "",
    "${STEP}How to fix:",
    "$STEP$STEP- Check that the directory is readable, then run the check again",
    "$STEP$STEP- If it is, report this at https://github.com/TBSten/katachi/issues with the cause above",
)

private fun uncheckedCheckBlock(violation: UncheckedCheck, root: String?): List<String> = listOf(
    "[${violation.label}] ${displayExistingPath(root, violation.path)}",
    "${STEP}Katachi failed while running ${violation.check}, so nothing it would have reported is known.",
    *causeLines(violation.cause).toTypedArray(),
    "",
    "${STEP}How to fix:",
    "$STEP$STEP- Read the cause above and fix the check, or stop passing it to assert()",
    "$STEP$STEP- Report it at https://github.com/TBSten/katachi/issues if the check is one of katachi's",
)

private fun foreignBlock(violation: Violation, root: String?): List<String> = buildList {
    // A blank path has nothing to resolve: `<empty>` stays the marker it is rather than
    // turning into a URI of a file named `<empty>`.
    val path = violation.path.takeIf { it.isNotBlank() }?.let { displayPath(root, oneLine(it)) }
    add("[${oneLine(violation.label)}] ${path ?: oneLine(violation.path)}")
    for (detail in violation.details) add("$STEP${oneLine(detail.label)}: ${oneLine(detail.value)}")
}

// A newline in a label would break what agents grep for (`^\[`).
internal fun oneLine(value: String): String =
    value.lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { "<empty>" }

// Stack trace deliberately omitted: it points into katachi, not the project.
internal fun causeLine(cause: Throwable): String {
    val type = cause::class.qualifiedName ?: cause::class.java.name
    val message = cause.message?.lineSequence()?.firstOrNull()?.trim()
    return if (message.isNullOrEmpty()) type else "$type: $message"
}

// Stack trace deliberately omitted: it points into katachi, not the project.
internal fun causeLines(cause: Throwable): List<String> {
    val rest = cause.message?.lines()?.drop(1).orEmpty()
        .map { it.trimEnd() }
        .filter { it.isNotEmpty() }
        .map { "$STEP$STEP$it" }
    return listOf("${STEP}Cause: ${causeLine(cause)}") + rest
}

private fun fragment(lines: List<String>): List<String> = lines.map { "$STEP$STEP$STEP$STEP$it" }

/**
 * [report], for the tests of katachi's other modules, which cannot reach it: they check the text a failing `assert()`
 * prints for the violations they produce.
 *
 * ## Example 1: in a test of katachi-konsist
 * ```kt
 * projectArchitecture.validate(RealFileSystem(fixtureRoot)).reportForTest() shouldContain "must"
 * ```
 */
@InternalKatachiApi
public fun List<Violation>.reportForTest(
    maxViolations: Int = DEFAULT_MAX_VIOLATIONS,
    projectRoot: FsPath? = null,
): String = report(maxViolations, projectRoot)
