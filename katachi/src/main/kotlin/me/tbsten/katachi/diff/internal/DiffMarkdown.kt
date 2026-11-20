package me.tbsten.katachi.diff.internal

/**
 * The names of the Markdown files, all in one directory: the summary, and the file listing named
 * after it (`diff-report.md` -> `diff-report.files-in-no-role.md`).
 */
internal class MarkdownFileNames(val summary: String) {
    private val base: String = summary.removeSuffix(".md")
    val filesInNoRole: String = "$base.files-in-no-role.md"

    /** Every name a run can produce, so it can remove what an earlier run left behind. */
    val all: List<String> get() = listOf(summary, filesInNoRole)
}

/**
 * The Markdown report as file name to content: the summary, and the listing of the files in no role
 * when there are any.
 *
 * The listing is kept out of the summary so that the summary stays short enough to paste into a
 * pull request comment however large the change is. The files of each role are not listed twice:
 * the summary's table already goes down to them.
 */
// GitHub renders `<details>` in Markdown, which is what lets an unchanged group stay folded.
internal fun renderDiffMarkdown(model: DiffReportModel, names: MarkdownFileNames): Map<String, String> {
    val roles = model.nodes.flatMap { it.roles() }.filter { it.changed }
    val listings = buildMap {
        if (model.unassigned.isNotEmpty()) put(names.filesInNoRole, filesInNoRole(model, names))
    }
    return mapOf(names.summary to summary(model, touched = roles.size, listings = listings.keys, names)) + listings
}

private fun summary(model: DiffReportModel, touched: Int, listings: Set<String>, names: MarkdownFileNames): String = buildString {
    val unassigned = if (model.unassigned.isEmpty()) "" else ", ${files(model.unassigned.size)} in no role"
    val removed = if (model.removed.isEmpty()) "" else ", ${files(model.removed.size)} in no role deleted"
    val ignored = if (model.ignored.isEmpty()) "" else ", ${files(model.ignored.size)} ignored"
    appendLine("# Architecture diff report: ${model.range.label}")
    appendLine()
    appendLine(
        "${files(model.changes.size)} changed, +${model.added} -${model.deleted}. " +
            "$touched of ${model.nodes.sumOf { it.roles().size }} roles touched$unassigned$removed$ignored.",
    )
    appendLine()
    if (model.hiddenCount > 0) {
        appendLine(
            "Showing ${model.shown.size} of ${model.changes.size} files (maxFiles=${model.maxFiles}). " +
                "Raise maxFiles to see all.",
        )
        appendLine()
    }
    if (touched == 0) {
        appendLine("No role was touched by this change.")
        appendLine()
    }
    if (model.unassigned.isNotEmpty()) noRoleAlert(model.unassigned.size)
    // The tree ends at files, as the HTML does: group -> role -> file, then the files no role allows.
    if (model.changes.isNotEmpty()) {
        val full = model.fullImportanceLines
        appendLine("| Role | Importance | Diff&nbsp;lines | Files |")
        appendLine("|---|---|---:|---:|")
        for (node in model.nodes.filter { it.changed }) summaryRows(node, model, depth = 0)
        looseRows("no role", model.unassigned, model)
        looseRows(REMOVED_LABEL, model.removed, model)
        looseRows("ignored", model.ignored, model)
        appendLine()
        appendLine("_Importance: diff lines × weight, out of $full (${heatSquares(HEAT_LEVELS)} at $full or more)._")
        appendLine()
        if (model.removed.isNotEmpty()) {
            appendLine("_($REMOVED_LABEL): ${REMOVED_NOTE}_")
            appendLine()
        }
        if (model.ignored.isNotEmpty()) {
            appendLine("_(ignored): ${IGNORED_NOTE}_")
            appendLine()
        }
    }
    // Every role appears once: a changed one in the table above (its files under it), an
    // unchanged one in this tree, under its groups, so the tree's leaves are always roles.
    val unchanged = model.nodes.sumOf { node -> node.roles().count { !it.changed } }
    if (unchanged > 0) {
        appendLine("<details><summary>Unchanged (${if (unchanged == 1) "1 role" else "$unchanged roles"})</summary>")
        appendLine()
        for (node in model.nodes) unchangedTree(node, depth = 0)
        appendLine()
        appendLine("</details>")
        appendLine()
    }
    // Relative links: they resolve next to this file both on GitHub and on disk.
    if (names.filesInNoRole in listings) {
        appendLine("${howToSeeLabel(model.range)} per file: [Files in no role](${names.filesInNoRole})")
    }
}

// A pull request's files are all on GitHub, so its rows carry the link alone. Any other range keeps
// the command: its commits may be unpushed, and the link then leads nowhere.
private fun showsGitDiffCommand(range: DiffRange): Boolean = range.pullRequest == null || range.github == null

private fun howToSeeLabel(range: DiffRange): String =
    if (showsGitDiffCommand(range)) "GitHub links and `git diff` commands" else "GitHub links"

// A directory `ignore()` leaves out is skipped by the architecture test too, so no warning: it will not fail on these.
private const val IGNORED_NOTE: String =
    "Files under a directory `ignore()` leaves out of the layout. The architecture test does not look at them."

private const val REMOVED_LABEL: String = "no role, deleted"

// No warning either: a deleted file is not there for the architecture test to fail on.
private const val REMOVED_NOTE: String =
    "Files no layout { } allows that this change deletes. The architecture test does not look at them any more."

// Files no role owns, below the roles: their importance is their raw diff lines, as no role's weight applies to them.
private fun StringBuilder.looseRows(label: String, changes: List<FileChange>, model: DiffReportModel) {
    if (changes.isEmpty()) return
    appendLine("| **($label)** | | ${diffLines(changes.sumOf { it.added }, changes.sumOf { it.deleted })} | ${changes.size} |")
    val shown = changes.filter { it in model.shown }
    for (change in shown) appendLine(fileSummaryRow(change.path, change, change.total.toDouble(), model.fullImportanceLines, depth = 1))
    moreSummaryRow(changes.size - shown.size, depth = 1)
}

// Files no layout { } allows are changes no role owns: keep them in view rather than folded away.
private fun filesInNoRole(model: DiffReportModel, names: MarkdownFileNames): String = buildString {
    appendLine("# Files in no role: ${model.range.label}")
    appendLine()
    listingPreamble(model, names)
    noRoleAlert(model.unassigned.size)
    appendLine("<details open><summary><b>No role</b> ${files(model.unassigned.size)}</summary>")
    appendLine()
    val shown = model.unassigned.filter { it in model.shown }
    fileTable(shown.map { fileRow(it.path, it, model.range) }, hidden = model.unassigned.size - shown.size)
    appendLine("</details>")
}

// A file no layout { } allows is not just listed: the architecture test reports it, so say so loudly.
private fun StringBuilder.noRoleAlert(count: Int) {
    appendLine("> [!WARNING]")
    appendLine(
        "> ${if (count == 1) "1 file is" else "$count files are"} in no role: no layout { } allows " +
            "${if (count == 1) "it" else "them"}, so the architecture test will fail on " +
            "${if (count == 1) "it" else "them"}.",
    )
    appendLine()
}

private fun StringBuilder.listingPreamble(model: DiffReportModel, names: MarkdownFileNames) {
    appendLine("Back to the [summary](${names.summary}).")
    appendLine()
    if (model.hiddenCount > 0) {
        appendLine(
            "Showing ${model.shown.size} of ${model.changes.size} files across the file listings " +
                "(maxFiles=${model.maxFiles}). Raise maxFiles to see all.",
        )
        appendLine()
    }
    if (showsGitDiffCommand(model.range)) {
        appendLine("_Run the `git diff` commands from the repository root._")
        appendLine()
    }
}


private fun StringBuilder.fileTable(rows: List<String>, hidden: Int) {
    if (rows.isNotEmpty()) {
        appendLine("| File | Diff&nbsp;lines | How to see |")
        appendLine("|---|---:|---|")
        for (row in rows) appendLine(row)
        appendLine()
    }
    if (hidden > 0) {
        appendLine("…and ${if (hidden == 1) "1 more file" else "$hidden more files"}")
        appendLine()
    }
}

// A group shows up only for the unchanged roles under it, so a changed group keeps just those.
private fun StringBuilder.unchangedTree(node: DiffNode, depth: Int) {
    if (node.roles().none { !it.changed }) return
    val name = if (node is DiffNode.GroupNode) "**${node.name}**" else node.name
    appendLine("${"  ".repeat(depth)}- $name")
    if (node is DiffNode.GroupNode) for (child in node.children) unchangedTree(child, depth + 1)
}

private fun StringBuilder.summaryRows(node: DiffNode, model: DiffReportModel, depth: Int) {
    val full = model.fullImportanceLines
    val name = if (node is DiffNode.GroupNode) "**${node.name}**" else node.name
    appendLine(
        "| ${indent(depth)}$name | ${heatSquares(heatLevel(node.score, full))} | " +
            "${diffLines(node.added, node.deleted)} | ${node.fileCount} |",
    )
    when (node) {
        is DiffNode.GroupNode ->
            for (child in node.children.filter { it.changed }) summaryRows(child, model, depth + 1)
        is DiffNode.RoleNode -> {
            val shown = node.files.filter { it.change in model.shown }
            for (file in shown) {
                appendLine(fileSummaryRow(file.shortPath, file.change, file.change.total * node.weight, full, depth + 1))
            }
            moreSummaryRow(node.files.size - shown.size, depth + 1)
        }
    }
}

// The file count column stays empty on a file row: it counts the files under a group or a role.
private fun fileSummaryRow(display: String, change: FileChange, score: Double, full: Int, depth: Int): String {
    val lines = if (change.binary) "binary" else diffLines(change.added, change.deleted)
    val status = if (change.status == ChangeStatus.Modified) "" else " _(${change.status.label})_"
    return "| ${indent(depth)}${cellCode(display)}$status | ${heatSquares(heatLevel(score, full))} | $lines | |"
}

private fun StringBuilder.moreSummaryRow(hidden: Int, depth: Int) {
    if (hidden > 0) appendLine("| ${indent(depth)}…and ${if (hidden == 1) "1 more file" else "$hidden more files"} | | | |")
}

private fun indent(depth: Int): String = "&nbsp;&nbsp;".repeat(depth) + if (depth > 0) "└ " else ""

private fun fileRow(display: String, change: FileChange, range: DiffRange): String {
    val lines = if (change.binary) "binary" else diffLines(change.added, change.deleted)
    val links = listOfNotNull(
        range.githubUrl(change.path)?.let { "[GitHub]($it)" },
        cellCode(range.gitDiffCommand(change)).takeIf { showsGitDiffCommand(range) },
    ).joinToString(" ")
    val status = if (change.status == ChangeStatus.Modified) "" else " _(${change.status.label})_"
    return "| ${cellCode(display)}$status | $lines | $links |"
}

// A table cell ends at the first `|` even inside a code span, and a backtick in a file name would end a
// single backtick span early, so both are escaped the way GitHub's tables expect.
private fun cellCode(text: String): String {
    val escaped = text.replace("|", "\\|").replace('\r', ' ').replace('\n', ' ')
    val fence = "`".repeat((Regex("`+").findAll(escaped).maxOfOrNull { it.value.length } ?: 0) + 1)
    val pad = if (escaped.startsWith("`") || escaped.endsWith("`")) " " else ""
    return "$fence$pad$escaped$pad$fence"
}

// A no-break space keeps "+25 -26" on one line: GitHub narrows the table's columns to their
// widest unbreakable text, and a cell that can break is the one that gives way.
private fun diffLines(added: Int, deleted: Int): String = "+$added&nbsp;-$deleted"

private fun files(count: Int): String = if (count == 1) "1 file" else "$count files"

// One square per level, up to five, yellow to red. A word joiner between the squares keeps them on
// one line: GitHub narrows a table's columns down to their widest unbreakable text, and the
// "Importance" heading alone is narrower than five squares.
private fun heatSquares(level: Int): String {
    val square = when (level) {
        0 -> return "⬜"
        1, 2 -> "🟨"
        3, 4 -> "🟧"
        else -> "🟥"
    }
    return List(level) { square }.joinToString(WORD_JOINER)
}

private const val WORD_JOINER = "\u2060"
