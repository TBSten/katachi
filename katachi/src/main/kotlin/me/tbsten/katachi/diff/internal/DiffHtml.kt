package me.tbsten.katachi.diff.internal

// One self-contained file: the report is opened from disk or attached to CI, where nothing else can be fetched.
internal fun renderDiffHtml(model: DiffReportModel, history: CommitHistory): String {
    val rows = buildString {
        var nextId = 0
        fun row(
            kind: String,
            parent: Int?,
            depth: Int,
            order: Int,
            added: Int,
            deleted: Int,
            weighted: Double,
            cells: String,
            extra: String = "",
        ): Int {
            val id = nextId++
            append(
                """<tr id="r$id" class="$kind" data-id="$id" data-parent="${parent ?: ""}" data-depth="$depth" """ +
                    """data-order="$order" data-added="$added" data-deleted="$deleted" data-weighted="$weighted"$extra>""",
            )
            append(cells)
            appendLine("</tr>")
            return id
        }

        fun nameCell(depth: Int, toggle: Boolean, label: String, sub: String?, anchor: String, anchorName: String): String =
            """<td class="name" style="--depth:$depth">""" +
                (if (toggle) """<button class="toggle" aria-label="toggle"></button>""" else """<span class="leaf"></span>""") +
                """<span class="label">$label</span>""" +
                (sub?.let { """<span class="sub">${it.escapeHtml()}</span>""" } ?: "") +
                anchorButton(anchor, anchorName) +
                "</td>"

        fun statCells(): String = """<td class="heat"><span class="bar"></span></td><td class="lines"></td><td class="links"></td>"""

        // Every changed file gets a row, so a narrower commit selection can bring one past maxFiles into view;
        // the ones maxFiles leaves out start "limited" and the script re-ranks them on every selection.
        fun fileRow(parent: Int?, depth: Int, order: Int, change: FileChange, shortPath: String, weight: Double, kind: String = "file") {
            val limited = if (change in model.shown) "" else " limited"
            val status = """ <span class="status ${change.status.name.lowercase()}">${change.status.label}</span>"""
            val github = model.range.githubUrl(change.path)
                ?.let { """<a href="${it.escapeHtml()}" class="copy" target="_blank" rel="noopener">GitHub ↗</a>""" } ?: ""
            val command = model.range.gitDiffCommand(change).escapeHtml()
            row(
                kind = kind + limited,
                parent = parent,
                depth = depth,
                order = order,
                added = change.added,
                deleted = change.deleted,
                weighted = change.total * weight,
                cells = """<td class="name" style="--depth:$depth"><span class="leaf"></span>""" +
                    """<code class="path" data-full="${change.path.escapeHtml()}" data-short="${shortPath.escapeHtml()}">""" +
                    "${shortPath.escapeHtml()}</code>$status" + anchorButton("file:${change.path}", change.path) + "</td>" +
                    """<td class="heat"><span class="bar"></span></td><td class="lines"></td>""" +
                    """<td class="links">$github<button class="copy" data-command="$command" title="$command">git diff</button></td>""",
                extra = """ data-binary="${change.binary}" data-key="${"file:${change.path}".escapeHtml()}"""",
            )
        }

        // Stands for the files maxFiles left out; the script keeps it last whatever the sort.
        fun moreRow(parent: Int, depth: Int, hidden: Int) {
            val label = if (hidden == 1) "…and 1 more file" else "…and $hidden more files"
            row(
                kind = if (hidden > 0) "file more" else "file more filtered",
                parent = parent,
                depth = depth,
                order = Int.MAX_VALUE,
                added = 0,
                deleted = 0,
                weighted = 0.0,
                cells = """<td class="name" style="--depth:$depth"><span class="leaf"></span>""" +
                    """<span class="label">$label</span></td>""" +
                    """<td class="heat"><span class="bar"></span></td><td class="lines"></td><td class="links"></td>""",
            )
        }

        fun node(node: DiffNode, parent: Int?, depth: Int, order: Int) {
            val kind = if (node is DiffNode.GroupNode) "group" else "role"
            val weight = (node as? DiffNode.RoleNode)?.weight
            val weightLabel = weight?.takeIf { it != 1.0 }?.let { """ <span class="weight">×${formatWeight(it)}</span>""" } ?: ""
            val id = row(
                kind = kind,
                parent = parent,
                depth = depth,
                order = order,
                added = node.added,
                deleted = node.deleted,
                weighted = node.score,
                cells = nameCell(
                    depth = depth,
                    toggle = node is DiffNode.GroupNode || node.changed,
                    label = node.name.escapeHtml() + weightLabel,
                    sub = files(node.fileCount),
                    anchor = node.qualifiedName,
                    anchorName = node.qualifiedName,
                ) + statCells(),
                // A stable key, so a shared link keeps pointing at the same row after the report is made again.
                extra = """ data-changed="${node.changed}" data-files="${node.fileCount}" data-key="${node.qualifiedName.escapeHtml()}"""" +
                    ((node as? DiffNode.RoleNode)?.let { """ data-weight="${it.weight}"""" } ?: ""),
            )
            when (node) {
                is DiffNode.GroupNode -> node.children.filter { it.changed }.forEachIndexed { index, child -> node(child, id, depth + 1, index) }
                is DiffNode.RoleNode -> {
                    node.files.forEachIndexed { index, file ->
                        fileRow(id, depth + 1, index, file.change, file.shortPath, node.weight)
                    }
                    moreRow(id, depth + 1, hidden = node.files.count { it.change !in model.shown })
                }
            }
        }

        // A group of the files no role owns, below the roles; `kind` is its class, and its file rows' with "-file".
        // Folded unless the architecture test fails on its files: the others are listed only so no changed file goes missing.
        fun looseGroup(kind: String, changes: List<FileChange>, order: Int, label: String, note: String, key: String, anchorName: String, folded: Boolean) {
            if (changes.isEmpty()) return
            val foldedByDefault = if (folded) """ data-collapsed-default="true"""" else ""
            val id = row(
                kind = "group $kind",
                parent = null,
                depth = 0,
                order = order,
                added = changes.sumOf { it.added },
                deleted = changes.sumOf { it.deleted },
                weighted = 0.0,
                cells = nameCell(0, toggle = true, label = label, sub = note, anchor = key, anchorName = anchorName) +
                    """<td class="heat"><span class="not-weighted">not weighted</span><span class="bar"></span></td>""" +
                    """<td class="lines"></td><td class="links"></td>""",
                extra = """ data-changed="true"$foldedByDefault data-files="${changes.size}" data-key="$key"""",
            )
            changes.forEachIndexed { index, change -> fileRow(id, 1, index, change, change.path, 0.0, kind = "file $kind-file") }
            moreRow(id, 1, hidden = changes.count { it !in model.shown })
        }

        model.nodes.filter { it.changed }.forEachIndexed { index, node -> node(node, null, 0, index) }
        val order = model.nodes.size
        looseGroup(
            kind = "unassigned", changes = model.unassigned, order = order, label = WARNING_ICON + "No role",
            note = noRoleWarning(model.unassigned.size), key = "no-role", anchorName = "the files in no role", folded = false,
        )
        looseGroup(
            kind = "baselined", changes = model.baselined, order = order + 1, label = "No role, held back by the baseline",
            note = baselinedNote(model.baselined.size), key = "baselined", anchorName = "the files the baseline holds back", folded = true,
        )
        looseGroup(
            kind = "removed", changes = model.removed, order = order + 2, label = "No role, deleted",
            note = removedNote(model.removed.size), key = "no-role-deleted", anchorName = "the deleted files in no role", folded = true,
        )
        looseGroup(
            kind = "ignored", changes = model.ignored, order = order + 3, label = "Ignored",
            note = ignoredNote(model.ignored.size), key = "ignored", anchorName = "the ignored files", folded = true,
        )
    }
    val touched = model.nodes.sumOf { node -> node.roles().count { it.changed } }
    val roleCount = model.nodes.sumOf { it.roles().size }
    return fillPlaceholders(
        HTML_TEMPLATE,
        mapOf(
            "STYLE" to DIFF_HTML_STYLE,
            "SCRIPT" to DIFF_HTML_SCRIPT,
            "TITLE" to "Architecture diff: ${model.range.label}".escapeHtml(),
            "HEADING" to heading(model.range),
            "SUMMARY_ANCHOR" to anchorButton("summary", "the summary"),
            "GENERATED" to generatedAt(),
            "FILES" to model.changes.size.toString(),
            "MAX_FILES" to model.maxFiles.toString(),
            "FULL_IMPORTANCE" to model.fullImportanceLines.toString(),
            "ADDED" to model.added.toString(),
            "DELETED" to model.deleted.toString(),
            "TOUCHED" to """<span data-stat="touched">$touched</span> <span class="of">of $roleCount</span>""",
            "UNASSIGNED" to looseStat("warn-stat", "no-role", model.unassigned.size, "in no role"),
            "BASELINED" to looseStat("baselined-stat", "baselined", model.baselined.size, "held back by the baseline"),
            "REMOVED" to looseStat("removed-stat", "no-role-deleted", model.removed.size, "in no role deleted"),
            "IGNORED" to looseStat("ignored-stat", "ignored", model.ignored.size, "ignored"),
            "LIMIT" to """<p class="limit" data-stat="limit"${if (model.hiddenCount == 0) " hidden" else ""}>""" +
                """Showing <span data-stat="shown">${model.shown.size}</span> of <span data-stat="limit-total">""" +
                """${model.changes.size}</span> files (maxFiles=${model.maxFiles}). Raise maxFiles to see all.</p>""",
            "UNCHANGED" to unchangedSection(model),
            "ROWS" to rows,
            "COMMITS" to commitsSection(history),
            "DATA" to commitHistoryJson(history),
        ),
    )
}

// One pass over the template, so what goes in for one placeholder is never read for another: file names,
// refs and commit subjects are the change author's text, and one spelling a placeholder must stay text.
private fun fillPlaceholders(template: String, values: Map<String, String>): String =
    PLACEHOLDER.replace(template) { match -> values[match.groupValues[1]] ?: match.value }

private val PLACEHOLDER: Regex = Regex("""\{\{([A-Z_]+)\}\}""")

// A header figure for a group of files no role owns; hidden while it counts none, as the script keeps it.
private fun looseStat(cssClass: String, stat: String, count: Int, caption: String): String =
    """<div class="$cssClass"${if (count == 0) " hidden" else ""}><span class="num" data-stat="$stat">""" +
        """$count</span><span class="cap"><span data-stat="$stat-noun">""" +
        """${if (count == 1) "file" else "files"}</span> $caption</span></div>"""

// Every role appears once: a changed one in the table, down to its files; an unchanged one here,
// under its groups, so this tree's leaves are always roles. Folded: it is what the change did not touch.
// Every role appears once: a changed one in the table, down to its files; an unchanged one here,
// under its groups, so this tree's leaves are always roles. Changed roles are here too, hidden, so the
// script can show one that a narrower commit selection leaves untouched.
private fun unchangedSection(model: DiffReportModel): String {
    val count = model.nodes.sumOf { node -> node.roles().count { !it.changed } }
    if (model.nodes.isEmpty()) return ""
    fun changedClass(node: DiffNode) = if (node.roles().none { !it.changed }) """ class="changed"""" else ""
    fun items(node: DiffNode, depth: Int): String {
        if (node.roles().isEmpty()) return ""
        val indent = if (depth > 0) """ style="padding-left: ${depth * 18}px"""" else ""
        val key = node.qualifiedName.escapeHtml()
        return when (node) {
            is DiffNode.RoleNode -> """<li$indent${changedClass(node)} data-key="$key">└ ${node.name.escapeHtml()}</li>"""
            is DiffNode.GroupNode -> """<li$indent${changedClass(node)} data-key="$key">└ <b>${node.name.escapeHtml()}</b></li>""" +
                node.children.joinToString("") { items(it, depth + 1) }
        }
    }
    // One column per top-level group; a role outside any group gets a column of its own name.
    val columns = model.nodes.filter { it.roles().isNotEmpty() }.joinToString("") { node ->
        val list = when (node) {
            is DiffNode.GroupNode -> node.children.joinToString("") { items(it, 0) }
            is DiffNode.RoleNode -> ""
        }
        """<div${changedClass(node)} data-column="${node.qualifiedName.escapeHtml()}"><div class="group-name" data-key="${node.qualifiedName.escapeHtml()}">""" +
            "${node.name.escapeHtml()}</div>" + (if (list.isEmpty()) "" else "<ul>$list</ul>") + "</div>"
    }
    val label = if (count == 1) "1 role" else "$count roles"
    return """<details class="unchanged" data-key="unchanged"${if (count == 0) " hidden" else ""}>""" +
        """<summary><span class="chev" aria-hidden="true"></span>""" +
        """<span class="title">Unchanged</span><span class="count" data-stat="unchanged">$label</span>""" +
        anchorButton("unchanged", "the unchanged roles") + "</summary>" +
        """<div class="unchanged-grid">$columns</div></details>"""
}

// The commits of the range with a checkbox each; the script re-adds the report for the ones checked.
private fun commitsSection(history: CommitHistory): String {
    val commits = history.commits
    if (commits.isEmpty()) return ""
    val rows = commits.joinToString("") { commit ->
        val label = if (commit.sha == WORKING_TREE) "working tree" else commit.short
        """<li><label><input type="checkbox" data-commit="${commit.short.escapeHtml()}" checked>""" +
            """<code>${label.escapeHtml()}</code><span class="subject">${commit.subject.escapeHtml()}</span>""" +
            """<span class="author">${commit.author.escapeHtml()}</span><span class="date">${commit.date.take(10).escapeHtml()}</span>""" +
            "</label></li>"
    }
    val omitted = if (history.omitted > 0) {
        """<p class="omitted">${history.omitted} older commits are left out: only the newest $MAX_COMMITS are listed.</p>"""
    } else {
        ""
    }
    // Starts closed: the script opens it only when the page loads with a narrower selection than "all".
    return """<details class="commits"><summary><span class="chev" aria-hidden="true"></span>""" +
        """<span class="title">Commits</span><span class="count"><span data-stat="selected">${commits.size}</span> of ${commits.size} selected</span>""" +
        """<span class="commit-actions"><button type="button" data-action="all-commits">all</button>""" +
        """<button type="button" data-action="no-commits">none</button></span></summary>""" +
        """<p class="approx" hidden>Non-adjacent commits: line counts are summed per commit and may exceed the net diff.</p>""" +
        "$omitted<ul>$rows</ul></details>"
}

// Lucide's link icon: copies a link that opens the report at this row, keeping the rest of the view.
private fun anchorButton(key: String, name: String): String =
    """<button class="anchor" data-anchor="${key.escapeHtml()}" aria-label="copy link to ${name.escapeHtml()}" """ +
        """title="Copy a link to ${name.escapeHtml()}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" """ +
        """stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">""" +
        """<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>""" +
        """<path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg></button>"""

private fun files(count: Int): String = if (count == 1) "1 file" else "$count files"

// 10.0 reads as ×10, 0.5 as ×0.5.
private fun formatWeight(weight: Double): String =
    if (weight == Math.floor(weight) && weight < 1e9) weight.toLong().toString() else weight.toString()

private fun generatedAt(): String =
    java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))

private fun heading(range: DiffRange): String {
    val label = range.label.escapeHtml()
    return label.replace("..", """ <span class="dots">..</span> """)
}

// Lucide's triangle-alert: a file no role allows is not just shown, it fails the architecture test.
private const val WARNING_ICON: String =
    """<svg class="warn" role="img" aria-label="warning" viewBox="0 0 24 24" fill="none" stroke="currentColor" """ +
        """stroke-width="2" stroke-linecap="round" stroke-linejoin="round">""" +
        """<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/>""" +
        """<path d="M12 9v4"/><path d="M12 17h.01"/></svg>"""

private fun noRoleWarning(count: Int): String =
    if (count == 1) {
        "1 file no layout { } allows. The architecture test will fail on it."
    } else {
        "$count files no layout { } allows. The architecture test will fail on them."
    }

private fun ignoredNote(count: Int): String =
    "${files(count)} under ignore(). The architecture test does not look at ${if (count == 1) "it" else "them"}."

private fun baselinedNote(count: Int): String =
    "${files(count)} no layout { } allows, held back by the baseline. " +
        "The architecture test does not fail on ${if (count == 1) "it" else "them"}."

private fun removedNote(count: Int): String =
    "${files(count)} no layout { } allows, deleted by this change. " +
        "The architecture test does not look at ${if (count == 1) "it" else "them"} any more."

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
