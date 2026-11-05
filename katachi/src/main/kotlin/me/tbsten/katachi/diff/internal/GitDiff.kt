package me.tbsten.katachi.diff.internal

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import me.tbsten.katachi.diff.KatachiDiffGitException
import me.tbsten.katachi.diff.KatachiDiffPullRequestException
import me.tbsten.katachi.dsl.files.FsPath

internal class FileChange(
    /** Project relative, `/` separated. The new path for a rename. */
    val path: String,
    /** The old path of a rename, project relative; null otherwise. */
    val previousPath: String?,
    val added: Int,
    val deleted: Int,
    val binary: Boolean,
    val status: ChangeStatus,
) {
    val total: Int get() = added + deleted
}

internal enum class ChangeStatus(val label: String) {
    Added("added"),
    Modified("modified"),
    Deleted("deleted"),
    Renamed("renamed"),
    Untracked("untracked"),
}

/** Which two trees are compared, and how the reader can look at one file of it again. */
internal class DiffRange(
    val before: String,
    /** Empty for the working tree. */
    val after: String,
    /** `...` for a pull request: GitHub compares against the merge base. */
    val separator: String,
    val beforeSha: String,
    val afterSha: String?,
    val pullRequest: PullRequest?,
    val github: GitHubRepository?,
    /** Where the project root sits inside the repository, `/` terminated or empty. */
    val repositoryPrefix: String,
) {
    val label: String
        get() = pullRequest?.let { "PR #${it.number}" }
            ?: if (after.isEmpty()) {
                "${pinned(before, beforeSha)}..(working tree)"
            } else {
                "${pinned(before, beforeSha)}$separator${pinned(after, afterSha)}"
            }

    /**
     * The range as commit SHAs, resolved once when the report was made. The report and the commands
     * copied from it keep pointing at the same commits after `HEAD` or a branch moves on.
     */
    val pinnedRefs: String
        get() = afterSha?.let { "$beforeSha$separator$it" } ?: beforeSha

    /**
     * A command that shows [change] again, run from the repository root (the paths GitHub shows).
     * `git diff` never shows an untracked file, so that one is compared with an empty file instead.
     */
    fun gitDiffCommand(change: FileChange): String {
        val path = repositoryPrefix + change.path
        return if (change.status == ChangeStatus.Untracked) {
            "git diff --no-index -- /dev/null $path"
        } else {
            "git diff $pinnedRefs -- $path"
        }
    }

    /** A link to [path]'s diff on GitHub, or null when the diff has no counterpart there (the working tree). */
    fun githubUrl(path: String): String? {
        val repository = github ?: return null
        val anchor = "diff-" + sha256(repositoryPrefix + path)
        pullRequest?.let { return "${it.url}/files#$anchor" }
        val head = afterSha ?: return null
        return "${repository.webUrl}/compare/$beforeSha$separator$head#$anchor"
    }
}

// The ref the reader typed stays readable; the short SHA says which commit it meant when the report was made.
private fun pinned(ref: String, sha: String?): String =
    if (sha == null || sha.startsWith(ref)) ref else "$ref (${sha.take(7)})"

internal class PullRequest(val number: Int, val url: String)

internal class GitHubRepository(val owner: String, val name: String) {
    val webUrl: String get() = "https://github.com/$owner/$name"
}

internal fun resolveDiffRange(root: FsPath, before: String, after: String, pr: String): DiffRange {
    val github = githubRepositoryOf(root)
    val prefix = git(root, "rev-parse", "--show-prefix").trim()
    if (pr.isNotEmpty()) {
        val view = gh(
            root,
            pr,
            "view",
            pr,
            "--json",
            "number,url,baseRefOid,headRefOid",
            "--jq",
            ".number,.url,.baseRefOid,.headRefOid",
        )
        val (number, url, base, head) = view.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .takeIf { it.size == 4 } ?: throw KatachiDiffPullRequestException(pr, "Unexpected output: $view")
        return DiffRange(
            before = base,
            after = head,
            separator = "...",
            beforeSha = base,
            afterSha = head,
            pullRequest = PullRequest(number.toIntOrNull() ?: 0, url),
            github = github,
            repositoryPrefix = prefix,
        )
    }
    return DiffRange(
        before = before,
        after = after,
        separator = "..",
        beforeSha = revParse(root, before),
        afterSha = after.takeIf { it.isNotEmpty() }?.let { revParse(root, it) },
        pullRequest = null,
        github = github,
        repositoryPrefix = prefix,
    )
}

internal fun changedFiles(root: FsPath, range: DiffRange): List<FileChange> {
    // --relative keeps paths project relative and drops changes outside the project, the part the
    // definition can say nothing about.
    val numstat = git(root, "diff", "--numstat", "-z", "-M", "--relative", range.pinnedRefs)
    val statuses = nameStatus(git(root, "diff", "--name-status", "-z", "-M", "--relative", range.pinnedRefs))
    val changes = parseNumstat(numstat, statuses)
    if (range.after.isNotEmpty()) return changes
    val untracked = git(root, "ls-files", "--others", "--exclude-standard", "-z")
        .split('\u0000').filter { it.isNotEmpty() }
        .map { path ->
            val lines = runCatching { File(root.value, path).readLines().size }.getOrDefault(0)
            FileChange(path, null, added = lines, deleted = 0, binary = false, status = ChangeStatus.Untracked)
        }
    return changes + untracked
}

// `-z` numstat: "<added>\t<deleted>\t<path>\0", or for a rename "<added>\t<deleted>\t\0<old>\0<new>\0".
internal fun parseNumstat(output: String, statuses: Map<String, ChangeStatus>): List<FileChange> {
    val tokens = output.split('\u0000')
    val changes = mutableListOf<FileChange>()
    var index = 0
    while (index < tokens.size) {
        val head = tokens[index++]
        if (head.isEmpty()) continue
        val parts = head.split('\t', limit = 3)
        if (parts.size < 3) continue
        val binary = parts[0] == "-"
        val added = parts[0].toIntOrNull() ?: 0
        val deleted = parts[1].toIntOrNull() ?: 0
        val (previous, path) = if (parts[2].isEmpty()) {
            val old = tokens.getOrElse(index++) { "" }
            val new = tokens.getOrElse(index++) { "" }
            old to new
        } else {
            null to parts[2]
        }
        val status = if (previous != null) ChangeStatus.Renamed else statuses[path] ?: ChangeStatus.Modified
        changes += FileChange(path, previous, added, deleted, binary, status)
    }
    return changes
}

internal fun nameStatus(output: String): Map<String, ChangeStatus> {
    val tokens = output.split('\u0000')
    val statuses = mutableMapOf<String, ChangeStatus>()
    var index = 0
    while (index < tokens.size) {
        val code = tokens[index++]
        if (code.isEmpty()) continue
        when (code.first()) {
            'R', 'C' -> {
                index++
                statuses[tokens.getOrElse(index++) { "" }] = ChangeStatus.Renamed
            }
            'A' -> statuses[tokens.getOrElse(index++) { "" }] = ChangeStatus.Added
            'D' -> statuses[tokens.getOrElse(index++) { "" }] = ChangeStatus.Deleted
            else -> statuses[tokens.getOrElse(index++) { "" }] = ChangeStatus.Modified
        }
    }
    return statuses
}

internal fun revParse(root: FsPath, ref: String): String = git(root, "rev-parse", "--verify", "$ref^{commit}").trim()

private fun githubRepositoryOf(root: FsPath): GitHubRepository? {
    val url = runCatching { git(root, "remote", "get-url", "origin").trim() }.getOrNull() ?: return null
    val match = GITHUB_REMOTE.find(url) ?: return null
    return GitHubRepository(owner = match.groupValues[1], name = match.groupValues[2].removeSuffix(".git"))
}

private val GITHUB_REMOTE: Regex = Regex("""github\.com[:/]([^/]+)/([^/]+?)(?:\.git)?/?$""")

internal fun git(root: FsPath, vararg args: String): String {
    val result = run(root, listOf("git", *args))
    if (result.exitCode != 0) throw KatachiDiffGitException(command = "git ${args.joinToString(" ")}", stderr = result.stderr)
    return result.stdout
}

private fun gh(root: FsPath, pr: String, vararg args: String): String {
    val result = run(root, listOf("gh", "pr", *args))
    if (result.exitCode != 0) throw KatachiDiffPullRequestException(pr = pr, stderr = result.stderr)
    return result.stdout
}

private class CommandResult(val exitCode: Int, val stdout: String, val stderr: String)

private fun run(root: FsPath, command: List<String>): CommandResult {
    // stderr goes to a file rather than a pipe: two undrained pipes can deadlock the child.
    val stderrFile = File.createTempFile("katachi-diff", ".stderr")
    try {
        val process = try {
            ProcessBuilder(command)
                .directory(File(root.value))
                .redirectError(stderrFile)
                .start()
        } catch (cause: IOException) {
            return CommandResult(exitCode = -1, stdout = "", stderr = cause.message.orEmpty())
        }
        val stdout = process.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return CommandResult(exitCode = -1, stdout = stdout, stderr = "Timed out after $COMMAND_TIMEOUT_SECONDS seconds.")
        }
        return CommandResult(process.exitValue(), stdout, stderrFile.readText().trim())
    } finally {
        stderrFile.delete()
    }
}

private const val COMMAND_TIMEOUT_SECONDS: Long = 120L

// GitHub anchors each file of a diff page at `diff-<sha256 of its repository relative path>`.
private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
