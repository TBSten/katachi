package me.tbsten.katachi.diff.internal

import me.tbsten.katachi.dsl.files.FsPath

/** One commit of the range, or the uncommitted changes, with what it changed file by file. */
internal class CommitEntry(
    /** The full SHA; [WORKING_TREE] for the uncommitted changes. */
    val sha: String,
    /** What the URL and the list show: the abbreviated SHA, or [WORKING_TREE]. */
    val short: String,
    val subject: String,
    val author: String,
    /** ISO 8601; empty for the working tree. */
    val date: String,
    val changes: List<FileChange>,
)

/** The commits a report covers, oldest first, and how many older ones were left out. */
internal class CommitHistory(val commits: List<CommitEntry>, val omitted: Int)

/**
 * The commits between the two ends of [range] along the first parent, each with its own numstat, plus
 * the working tree when the range ends there. Commits that change no file of the project are left out.
 *
 * Only the newest [MAX_COMMITS] are kept: the page re-adds these per commit, and a longer history makes
 * the embedded data large without making a single review easier.
 */
internal fun commitHistory(root: FsPath, range: DiffRange): CommitHistory {
    val end = range.afterSha ?: revParse(root, "HEAD")
    val records = git(
        root, "log", "--first-parent", "--reverse", "-z", "--format=%H%x1f%h%x1f%s%x1f%an%x1f%aI",
        "${range.beforeSha}..$end",
    ).split('\u0000').map { it.trim('\n') }.filter { it.isNotEmpty() }.map { it.split('\u001f') }.filter { it.size == 5 }
    val kept = records.takeLast(MAX_COMMITS)
    // A commit that changes nothing inside the project (only elsewhere in the repository) has nothing to select.
    val commits = kept.map { (sha, short, subject, author, date) ->
        val parent = runCatching { revParse(root, "$sha^") }.getOrNull() ?: EMPTY_TREE
        CommitEntry(sha, short, subject, author, date, changesBetween(root, parent, sha))
    }.filter { it.changes.isNotEmpty() }
    val workingTree = if (range.after.isEmpty() && range.pullRequest == null) {
        val head = revParse(root, "HEAD")
        val headRange = DiffRange(
            before = "HEAD", after = "", separator = "..", beforeSha = head, afterSha = null,
            pullRequest = null, github = null, repositoryPrefix = "",
        )
        listOf(CommitEntry(WORKING_TREE, WORKING_TREE, "Uncommitted changes", "", "", changedFiles(root, headRange)))
            .filter { it.changes.isNotEmpty() }
    } else {
        emptyList()
    }
    return CommitHistory(commits + workingTree, omitted = records.size - kept.size)
}

private fun changesBetween(root: FsPath, from: String, to: String): List<FileChange> {
    val numstat = git(root, "diff", "--numstat", "-z", "-M", "--relative", from, to)
    val statuses = nameStatus(git(root, "diff", "--name-status", "-z", "-M", "--relative", from, to))
    return parseNumstat(numstat, statuses)
}

/** The data the page re-adds per selected commit: the commits, and for each changed path its lines per commit. */
internal fun commitHistoryJson(history: CommitHistory): String = buildString {
    append("{\"omitted\":").append(history.omitted).append(",\"commits\":[")
    history.commits.forEachIndexed { index, commit ->
        if (index > 0) append(',')
        append("{\"sha\":").append(jsonString(commit.sha))
        append(",\"short\":").append(jsonString(commit.short))
        append(",\"subject\":").append(jsonString(commit.subject))
        append(",\"author\":").append(jsonString(commit.author))
        append(",\"date\":").append(jsonString(commit.date)).append('}')
    }
    append("],\"files\":{")
    // path -> [[commit index, added, deleted, binary (0/1), status letter], ...]
    val byPath = linkedMapOf<String, MutableList<String>>()
    history.commits.forEachIndexed { index, commit ->
        for (change in commit.changes) {
            byPath.getOrPut(change.path) { mutableListOf() } +=
                "[$index,${change.added},${change.deleted},${if (change.binary) 1 else 0},\"${change.status.name.first()}\"]"
        }
    }
    byPath.entries.forEachIndexed { index, (path, entries) ->
        if (index > 0) append(',')
        append(jsonString(path)).append(":[").append(entries.joinToString(",")).append(']')
    }
    append("}}")
}

// Escapes for a JSON string that also sits inside a <script> element: "</" would end the element.
private fun jsonString(value: String): String = buildString {
    append('"')
    for (char in value) {
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '<' -> append("\\u003c")
            char < ' ' -> append("\\u%04x".format(char.code))
            else -> append(char)
        }
    }
    append('"')
}

/** The key the working tree's uncommitted changes go by, in the data and in the URL. */
internal const val WORKING_TREE: String = "worktree"

/** How many commits the page offers at most; older ones are left out with a note. */
internal const val MAX_COMMITS: Int = 200

// git's well-known empty tree, the "parent" of a root commit.
private const val EMPTY_TREE: String = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
