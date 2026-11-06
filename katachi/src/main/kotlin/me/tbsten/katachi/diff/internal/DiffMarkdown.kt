package me.tbsten.katachi.diff.internal

/**
 * The names of the Markdown files, all in one directory: the summary, and the two file listings
 * named after it (`diff-report.md` -> `diff-report.files-by-role.md`, `diff-report.files-in-no-role.md`).
 */
internal class MarkdownFileNames(val summary: String) {
    private val base: String = summary.removeSuffix(".md")
    val filesByRole: String = "$base.files-by-role.md"
    val filesInNoRole: String = "$base.files-in-no-role.md"

    /** Every name a run can produce, so it can remove what an earlier run left behind. */
    val all: List<String> get() = listOf(summary, filesByRole, filesInNoRole)
}

/**
 * The Markdown report as file name to content: the summary, and each file listing that has
 * something in it.
 *
 * The listings are kept out of the summary so that the summary stays short enough to paste into a
 * pull request comment however large the change is.
 */
// GitHub renders `<details>` in Markdown, which is what lets an unchanged group stay folded.
internal fun renderDiffMarkdown(model: DiffReportModel, names: MarkdownFileNames): Map<String, String> {
    val roles = model.nodes.flatMap { it.roles() }.filter { it.changed }
    val listings = buildMap {
        if (roles.isNotEmpty()) put(names.filesByRole, filesByRole(model, roles, names))
        if (model.unassigned.isNotEmpty()) put(names.filesInNoRole, filesInNoRole(model, names))
    }
    return mapOf(names.summary to summary(model, touched = roles.size, listings = listings.keys, names)) + listings
}

private fun summary(model: DiffReportModel, touched: Int, listings: Set<String>, names: MarkdownFileNames): String = buildString {
    val unassigned = if (model.unassigned.isEmpty()) "" else ", ${files(model.unassigned.size)} in no role"
    appendLine("# Architecture diff report: ${model.range.label}")
    appendLine()
    appendLine(
        "${files(model.changes.size)} changed, +${model.added} -${model.deleted}. " +
            "$touched of ${model.nodes.sumOf { it.roles().size }} roles touched$unassigned.",
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
        val maxFileScore = maxFileScore(model)
        appendLine("| Role | Importance | Diff&nbsp;lines | Files |")
        appendLine("|---|---|---:|---:|")
        for (node in model.nodes.filter { it.changed }) summaryRows(node, model, maxFileScore, depth = 0)
        if (model.unassigned.isNotEmpty()) {
            appendLine(
                "| **(no role)** | | ${diffLines(model.unassigned.sumOf { it.added }, model.unassigned.sumOf { it.deleted })} | " +
                    "${model.unassigned.size} |",
            )
            val shown = model.unassigned.filter { it in model.shown }
            for (change in shown) appendLine(fileSummaryRow(change.path, change, change.total.toDouble(), maxFileScore, depth = 1))
            moreSummaryRow(model.unassigned.size - shown.size, depth = 1)
        }
        appendLine()
    }
    // Every role appears once: a changed one in the table above (its files in the listing), an
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
    val links = listOfNotNull(
        "[Files by role](${names.filesByRole})".takeIf { names.filesByRole in listings },
        "[Files in no role](${names.filesInNoRole})".takeIf { names.filesInNoRole in listings },
    )
    if (links.isNotEmpty()) appendLine("GitHub links and `git diff` commands per file: ${links.joinToString(" · ")}")
}

private fun filesByRole(model: DiffReportModel, roles: List<DiffNode.RoleNode>, names: MarkdownFileNames): String = buildString {
    appendLine("# Files by role: ${model.range.label}")
    appendLine()
    listingPreamble(model, names)
    for (role in roles) {
        appendLine(
            "<details open><summary><b>${role.qualifiedName}</b> ${heatSquares(heatLevel(role.score, model.maxRoleScore))} " +
                "${files(role.fileCount)}, +${role.added} -${role.deleted}</summary>",
        )
        appendLine()
        val shown = role.files.filter { it.change in model.shown }
        fileTable(shown.map { fileRow(it.shortPath, it.change, model.range) }, hidden = role.files.size - shown.size)
        appendLine("</details>")
        appendLine()
    }
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
    appendLine("_Run the `git diff` commands from the repository root._")
    appendLine()
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

private fun StringBuilder.summaryRows(node: DiffNode, model: DiffReportModel, maxFileScore: Double, depth: Int) {
    val max = if (node is DiffNode.GroupNode) model.maxGroupScore else model.maxRoleScore
    val name = if (node is DiffNode.GroupNode) "**${node.name}**" else node.name
    appendLine(
        "| ${indent(depth)}$name | ${heatSquares(heatLevel(node.score, max))} | " +
            "${diffLines(node.added, node.deleted)} | ${node.fileCount} |",
    )
    when (node) {
        is DiffNode.GroupNode ->
            for (child in node.children.filter { it.changed }) summaryRows(child, model, maxFileScore, depth + 1)
        is DiffNode.RoleNode -> {
            val shown = node.files.filter { it.change in model.shown }
            for (file in shown) {
                appendLine(fileSummaryRow(file.shortPath, file.change, file.change.total * node.weight, maxFileScore, depth + 1))
            }
            moreSummaryRow(node.files.size - shown.size, depth + 1)
        }
    }
}

// The file count column stays empty on a file row: it counts the files under a group or a role.
private fun fileSummaryRow(display: String, change: FileChange, score: Double, maxFileScore: Double, depth: Int): String {
    val lines = if (change.binary) "binary" else diffLines(change.added, change.deleted)
    val status = if (change.status == ChangeStatus.Modified) "" else " _(${change.status.label})_"
    return "| ${indent(depth)}`$display`$status | ${heatSquares(heatLevel(score, maxFileScore))} | $lines | |"
}

private fun StringBuilder.moreSummaryRow(hidden: Int, depth: Int) {
    if (hidden > 0) appendLine("| ${indent(depth)}…and ${if (hidden == 1) "1 more file" else "$hidden more files"} | | | |")
}

// Weighted like the role it sits under; a file no role allows counts its lines as they are, as in the HTML.
private fun maxFileScore(model: DiffReportModel): Double {
    val roleFiles = model.nodes.flatMap { it.roles() }.flatMap { role -> role.files.map { it.change.total * role.weight } }
    val unassigned = model.unassigned.map { it.total.toDouble() }
    return (roleFiles + unassigned).maxOrNull() ?: 0.0
}

private fun indent(depth: Int): String = "&nbsp;&nbsp;".repeat(depth) + if (depth > 0) "└ " else ""

private fun fileRow(display: String, change: FileChange, range: DiffRange): String {
    val lines = if (change.binary) "binary" else diffLines(change.added, change.deleted)
    val links = listOfNotNull(
        range.githubUrl(change.path)?.let { "[GitHub]($it)" },
        "`${range.gitDiffCommand(change)}`",
    ).joinToString(" ")
    val status = if (change.status == ChangeStatus.Modified) "" else " _(${change.status.label})_"
    return "| `${display}`$status | $lines | $links |"
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
