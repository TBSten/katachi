package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.InternalKatachiApi

/** How many blocks `assert()` prints before it stops and counts the rest. */
public const val DEFAULT_MAX_VIOLATIONS: Int = 10

/** Indent of everything inside a block. A fragment meant to be copied adds another step. */
private const val STEP: String = "  "

/**
 * The violations, written out for the standard output of a test.
 *
 * What an agent reads is that output, so what is not in it does not exist; and what is good
 * for an agent turns out to be good for a person too. Hence: no colour and no character
 * outside ASCII, one block per violation with its parts always in the same order, every path
 * relative to the project root, and the counts on the first line rather than the last,
 * because long output gets cut off at the end.
 *
 * @param maxViolations how many blocks to write. The line after the last one says how many
 *   were left out; the summary counts them all either way.
 */
@InternalKatachiApi
public fun List<Violation>.report(maxViolations: Int = DEFAULT_MAX_VIOLATIONS): String {
    val shown = take(maxViolations.coerceAtLeast(0))
    val hidden = size - shown.size
    return buildString {
        append(summaryLine(this@report))
        for (violation in shown) {
            append("\n\n")
            append(blockOf(violation).joinToString("\n"))
        }
        if (hidden > 0) append("\n\nShowing first ${shown.size} ($hidden more)")
        // After the truncation line, and outside it: this is the only sentence saying the
        // result is partial, and a reader who does not see it is entitled to believe every
        // path was looked at. Cutting it away with the blocks would make the report lie.
        uncheckedLine(this@report)?.let { append("\n\n$it") }
    }
}

private fun summaryLine(violations: List<Violation>): String {
    val counted = ViolationKind.entries
        .map { kind -> kind to violations.count { it.kind == kind } }
        .filter { (_, count) -> count > 0 }
        .joinToString(", ") { (kind, count) -> "$kind: $count" }
    val total = "${violations.size} ${if (violations.size == 1) "violation" else "violations"}"
    return if (counted.isEmpty()) {
        "Katachi check failed: $total"
    } else {
        "Katachi check failed: $total ($counted)"
    }
}

/**
 * How many paths the run could not look at, in a sentence, or `null` when it looked at all
 * of them.
 *
 * The wording follows what actually failed: files, directories, or `paths` when both did,
 * because no single noun covers the two and a report that says "files" about a directory
 * sends the reader to the wrong place.
 */
private fun uncheckedLine(violations: List<Violation>): String? {
    val files = violations.count { it is UncheckedFile }
    val directories = violations.count { it is UncheckedDirectory }
    val total = files + directories
    if (total == 0) return null
    val noun = when {
        directories == 0 -> if (files == 1) "file" else "files"
        files == 0 -> if (directories == 1) "directory" else "directories"
        else -> "paths"
    }
    return "$total $noun could not be checked."
}

private fun blockOf(violation: Violation): List<String> = when (violation) {
    is UnexpectedFile -> unexpectedFileBlock(violation)
    is UnexpectedDirectory -> unexpectedDirectoryBlock(violation)
    is MissingFile -> missingFileBlock(violation)
    is UncheckedFile -> uncheckedFileBlock(violation)
    is UncheckedDirectory -> uncheckedDirectoryBlock(violation)
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

private fun uncheckedDirectoryBlock(violation: UncheckedDirectory): List<String> = listOf(
    "[${violation.label}] ${violation.path}",
    "${STEP}Katachi failed while checking this directory. Nothing below it was checked.",
    "${STEP}Cause: ${causeLine(violation.cause)}",
    "${STEP}How to fix:",
    "$STEP$STEP- Check that the directory is readable, then run the check again",
    "$STEP$STEP- If it is, report this at https://github.com/TBSten/katachi/issues with the cause above",
)

/**
 * A failure in one line: its type and the first line of its message, which is what an issue
 * needs to be worth opening.
 *
 * The stack trace is left out. It points into katachi rather than at the project, and
 * printing one per block would bury every violation the reader came for. Only the first line
 * of the message is kept for the same reason: a message that wraps would break out of the
 * block's shape.
 */
private fun causeLine(cause: Throwable): String {
    val type = cause::class.qualifiedName ?: cause::class.java.name
    val message = cause.message?.lineSequence()?.firstOrNull()?.trim()
    return if (message.isNullOrEmpty()) type else "$type: $message"
}

/** Indents a copyable fragment one step further than the list it hangs under. */
private fun fragment(lines: List<String>): List<String> = lines.map { "$STEP$STEP$STEP$STEP$it" }
