package me.tbsten.katachi.check

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.scan.AmbiguousLayout
import me.tbsten.katachi.scan.MissingDescription
import me.tbsten.katachi.scan.MissingFile
import me.tbsten.katachi.scan.Severity
import me.tbsten.katachi.scan.UncheckedCheck
import me.tbsten.katachi.scan.UncheckedConstraint
import me.tbsten.katachi.scan.UncheckedDirectory
import me.tbsten.katachi.scan.UncheckedDirectoryReason
import me.tbsten.katachi.scan.UncheckedFile
import me.tbsten.katachi.scan.UnexpectedDirectory
import me.tbsten.katachi.scan.UnexpectedFile
import me.tbsten.katachi.scan.UnsatisfiedConstraint
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.scan.ViolationKind

/**
 * How many blocks `assert()` prints before it stops and counts the rest.
 *
 * ## Example 1: show twice as many blocks as `report()` shows by default
 * ```kt
 * projectArchitecture.validate().report(maxViolations = DEFAULT_MAX_VIOLATIONS * 2)
 * ```
 */
public const val DEFAULT_MAX_VIOLATIONS: Int = 10

/**
 * Indent of everything inside a block. A fragment meant to be copied adds another step.
 *
 * `internal` rather than private so that `ConstraintBlocks.kt` — split off this file for size
 * — indents to the same depth by construction rather than by copying the string.
 */
internal const val STEP: String = "  "

/**
 * The violations, written out for the standard output of a test.
 *
 * What an agent reads is that output, so what is not in it does not exist; and what is good
 * for an agent turns out to be good for a person too. Hence: no colour and no character
 * outside ASCII, one block per violation with its parts always in the same order, every path
 * relative to the project root, and the counts on the first line rather than the last,
 * because long output gets cut off at the end.
 *
 * Only a [Severity.Error] violation can make `assert()` fail, so this text has three shapes:
 * - **Any error present** — the usual failure report (summary, error blocks, the error
 *   truncation line, the sentences saying what could not be checked), and — if there are any —
 *   a Warning section appended after it.
 * - **No errors, but warnings** — the Warning section alone. Nothing failed, so there is no
 *   failure summary to hang it under.
 * - **Neither** — an empty string.
 *
 * ## Example 1: build a report of the current violations without failing anything
 * ```kt
 * val report = projectArchitecture.validate().report()
 * println(report)
 * ```
 *
 * @param maxViolations the combined budget for the two sections: how many error blocks and how
 *   many warning blocks this writes out, together. A section with nothing in it spends none of
 *   it; when only one severity is present, that section gets the whole budget; when both are,
 *   warnings take a slice of it — never fewer than one block, never more than there are
 *   warnings — capped at a quarter, and errors take what is left. Either truncation line says
 *   how many more were left out of its own section; the summary counts every error either way.
 */
@InternalKatachiApi
public fun List<Violation>.report(maxViolations: Int = DEFAULT_MAX_VIOLATIONS): String {
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
                append(blockOf(violation).joinToString("\n"))
            }
            truncationLine(errors, shownErrors, noun = "", withBreakdown = true)?.let { append("\n\n$it") }
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
                append(blockOf(violation).joinToString("\n"))
            }
            truncationLine(warnings, shownWarnings, noun = " warnings", withBreakdown = false)
                ?.let { append("\n\n$it") }
        }
    }
}

/**
 * Which indices of [violations] a report shows when it can only afford [max] blocks, in
 * ascending order — chosen, never reordered, so the report keeps the caller's own order among
 * the blocks it does show.
 *
 * The budget is split fairly across the kinds that actually turned up, not across all of
 * [ViolationKind]: a run with only [ViolationKind.Unexpected] violations should not lose a slot
 * to a [ViolationKind.Missing] that has nothing to show. Every kind present gets one block
 * first, budget allowing; what is left is handed out one at a time, in [ViolationKind.entries]
 * order, to whichever kind still has more to show, until the budget or every kind runs out.
 *
 * [report] calls this once for the errors and once for the warnings, each with its own slice of
 * the overall budget, so [violations] here never mixes the two severities.
 */
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

/**
 * The failure report's first line: how many errors, broken down by kind, with the warning
 * count tacked on outside the parentheses.
 *
 * The warning count sits outside the per-kind breakdown on purpose: that breakdown answers
 * "why did the check fail", and folding a warning's kind into it would misread as one more
 * reason to fix something, when nothing here failed because of it.
 */
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

/**
 * The heading the Warning section opens with, whether it stands alone (nothing failed) or is
 * appended after a failure report.
 *
 * The second sentence stays the same regardless of the count on purpose. Written to agree with
 * the count ("does" / "do not fail the check"), it would sit right under a first line that just
 * said the check *did* fail whenever a run holds both errors and warnings, reading as if the
 * report contradicted itself one line down.
 */
private fun warningHeading(count: Int): String {
    val noun = if (count == 1) "warning" else "warnings"
    return "Katachi check found $count $noun. Warnings never fail the check."
}

/**
 * The "Showing first N (M more)" line for one severity's own slice of the budget — `null` when
 * nothing of that severity was left out.
 *
 * @param noun appended right after the shown count: `""` for the error section, so it reads
 *   `Showing first 10 (4 more: ...)` exactly as it always has, and `" warnings"` for the Warning
 *   section.
 * @param withBreakdown whether a kind-by-kind breakdown is appended when more than one kind is
 *   hiding something. Off for the Warning section: a warning's own kind already names it on the
 *   block's first line ([Violation.label]), so repeating the same two names here would say
 *   nothing a reader does not already have.
 */
private fun truncationLine(all: List<Violation>, shown: List<Violation>, noun: String, withBreakdown: Boolean): String? {
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

/**
 * Sentences at the end of a report saying what the run could not finish, in this order: paths
 * (files and/or directories), constraints, then checks that threw. Empty when the run saw all
 * of them.
 *
 * The paths sentence follows what actually failed: files, directories, or `paths` when both
 * did, because no single noun covers the two and a report that says "files" about a directory
 * sends the reader to the wrong place.
 */
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
    val constraints = violations.count { it is UncheckedConstraint }
    if (constraints > 0) {
        add("$constraints ${if (constraints == 1) "constraint" else "constraints"} could not be evaluated.")
    }
    val checks = violations.count { it is UncheckedCheck }
    if (checks > 0) add("$checks ${if (checks == 1) "check" else "checks"} could not be run.")
}

private fun blockOf(violation: Violation): List<String> = when (violation) {
    is UnexpectedFile -> unexpectedFileBlock(violation)
    is UnexpectedDirectory -> unexpectedDirectoryBlock(violation)
    is MissingFile -> missingFileBlock(violation)
    is UncheckedFile -> uncheckedFileBlock(violation)
    is UncheckedDirectory -> uncheckedDirectoryBlock(violation)
    is UncheckedCheck -> uncheckedCheckBlock(violation)
    is UnsatisfiedConstraint -> unsatisfiedConstraintBlock(violation)
    is UncheckedConstraint -> uncheckedConstraintBlock(violation)
    is AmbiguousLayout -> ambiguousLayoutBlock(violation)
    is MissingDescription -> missingDescriptionBlock(violation)
    // A violation from outside katachi. The block is the first line plus the values it states
    // about itself, and nothing else: katachi does not know what it means, so it writes no
    // sentence about it and never offers a way to fix it. Compile-time exhaustiveness is lost
    // the moment `Violation` stops being sealed; `ViolationBlockCoverageSpec` gets it back for
    // katachi's own violations by checking every one of them has a branch above this line.
    else -> foreignBlock(violation)
}

private fun unexpectedFileBlock(violation: UnexpectedFile): List<String> = buildList {
    add("[${violation.label}] ${violation.path}")
    add("${STEP}No role is defined for this file.")
    if (violation.nearby.isNotEmpty()) {
        add("${STEP}Nearby locations:")
        val width = violation.nearby.maxOf { it.role.qualifiedName.length }
        for (location in violation.nearby) {
            add("$STEP$STEP${location.role.qualifiedName.padEnd(width)} ${location.directory}/")
        }
    }
    add("${STEP}How to fix:")
    if (violation.nearby.isNotEmpty()) add("$STEP$STEP- Move it to one of the locations above")
    add("$STEP$STEP- Delete it if it is not needed")
    add("$STEP$STEP- Add a new role for it:")
    addAll(fragment(roleSuggestionFor(violation.path, isDirectory = false)))
}

private fun unexpectedDirectoryBlock(violation: UnexpectedDirectory): List<String> = buildList {
    add("[${violation.label}] ${violation.path}")
    add("${STEP}No role is defined for this directory. Nothing below it was checked.")
    add("${STEP}How to fix:")
    add("$STEP$STEP- Delete it if it is not needed")
    add("$STEP$STEP- Declare what belongs in it in the layout of an existing role")
    add("$STEP$STEP- Add a new role for it:")
    addAll(fragment(roleSuggestionFor(violation.path, isDirectory = true)))
}

private fun missingFileBlock(violation: MissingFile): List<String> = listOf(
    "[${violation.label}] ${violation.path}",
    // Neutral on purpose: a new module fails this way once, whichever of the declaration and
    // the file is written first, and being told the definition is wrong would be misleading.
    "${STEP}No file has been created yet for role ${violation.role.qualifiedName}.",
    "${STEP}Declared at: ${violation.declaredAt}",
    "${STEP}How to fix:",
    "$STEP$STEP- If it is not implemented yet, this error is expected",
    "$STEP$STEP- If it is no longer needed, remove the declaration at ${violation.declaredAt}",
)

private fun uncheckedFileBlock(violation: UncheckedFile): List<String> = listOf(
    "[${violation.label}] ${violation.path}",
    "${STEP}Katachi failed while checking this file, so nothing is known about it.",
    "${STEP}Cause: ${causeLine(violation.cause)}",
    "${STEP}How to fix:",
    "$STEP$STEP- Check that the file is readable, then run the check again",
    "$STEP$STEP- If it is, report this at https://github.com/TBSten/katachi/issues with the cause above",
)

/**
 * A directory that threw.
 *
 * The two reasons differ in one sentence and in nothing else: what to do about an unreadable
 * directory is the same either way, and only what was lost with it differs.
 * [UncheckedDirectoryReason.NotWalked] costs everything below the directory;
 * [UncheckedDirectoryReason.ModulesNotDiscovered] costs the modules at and below it, while the
 * walk still looked inside — so reusing the first sentence for the second would tell the
 * reader nothing there was checked when it was.
 */
private fun uncheckedDirectoryBlock(violation: UncheckedDirectory): List<String> = listOf(
    "[${violation.label}] ${violation.path}",
    when (violation.reason) {
        UncheckedDirectoryReason.NotWalked ->
            "${STEP}Katachi failed while checking this directory. Nothing below it was checked."

        UncheckedDirectoryReason.ModulesNotDiscovered ->
            "${STEP}Katachi failed while looking for modules here, so a module key may have " +
                "expanded to fewer modules than the project has."
    },
    "${STEP}Cause: ${causeLine(violation.cause)}",
    "${STEP}How to fix:",
    "$STEP$STEP- Check that the directory is readable, then run the check again",
    "$STEP$STEP- If it is, report this at https://github.com/TBSten/katachi/issues with the cause above",
)

private fun uncheckedCheckBlock(violation: UncheckedCheck): List<String> = listOf(
    "[${violation.label}] ${violation.path}",
    "${STEP}Katachi failed while running ${violation.check}, so nothing it would have reported is known.",
    "${STEP}Cause: ${causeLine(violation.cause)}",
    "${STEP}How to fix:",
    "$STEP$STEP- Read the cause above and fix the check, or stop passing it to assert()",
    "$STEP$STEP- Report it at https://github.com/TBSten/katachi/issues if the check is one of katachi's",
)

/**
 * A violation declared outside katachi. The block is the first line plus what [Violation.details]
 * states about it, and nothing else: katachi does not know what the violation means, so it
 * writes no sentence about it and offers no "How to fix:".
 */
private fun foreignBlock(violation: Violation): List<String> = buildList {
    add("[${oneLine(violation.label)}] ${oneLine(violation.path)}")
    for (detail in violation.details) add("$STEP${oneLine(detail.label)}: ${oneLine(detail.value)}")
}

/**
 * Keeps a value written outside katachi — or by a user, such as a constraint name — inside one
 * line of a block. A newline in a label would break the very thing an agent greps for
 * (`grep -A 20 "^\["`). Trimming is not politeness here; it is what keeps the format a format.
 */
internal fun oneLine(value: String): String =
    value.lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { "<empty>" }

/**
 * A failure in one line: its type and the first line of its message, which is what an issue
 * needs to be worth opening.
 *
 * The stack trace is left out. It points into katachi rather than at the project, and
 * printing one per block would bury every violation the reader came for. Only the first line
 * of the message is kept for the same reason: a message that wraps would break out of the
 * block's shape.
 */
internal fun causeLine(cause: Throwable): String {
    val type = cause::class.qualifiedName ?: cause::class.java.name
    val message = cause.message?.lineSequence()?.firstOrNull()?.trim()
    return if (message.isNullOrEmpty()) type else "$type: $message"
}

/** Indents a copyable fragment one step further than the list it hangs under. */
private fun fragment(lines: List<String>): List<String> = lines.map { "$STEP$STEP$STEP$STEP$it" }
