package me.tbsten.katachi.test.diff

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.random.Random
import me.tbsten.katachi.check.internal.JsonNode
import me.tbsten.katachi.check.internal.JsonReader
import me.tbsten.katachi.diff.internal.ChangeStatus
import me.tbsten.katachi.diff.internal.CommitEntry
import me.tbsten.katachi.diff.internal.CommitHistory
import me.tbsten.katachi.diff.internal.DiffRange
import me.tbsten.katachi.diff.internal.DiffReportModel
import me.tbsten.katachi.diff.internal.FileChange
import me.tbsten.katachi.diff.internal.GitHubRepository
import me.tbsten.katachi.diff.internal.MarkdownFileNames
import me.tbsten.katachi.diff.internal.renderDiffHtml
import me.tbsten.katachi.diff.internal.renderDiffMarkdown
import me.tbsten.katachi.diff.internal.shellQuote
import me.tbsten.katachi.test.pbt.checkTrials
import me.tbsten.katachi.test.pbt.pbtSeed
import me.tbsten.katachi.test.pbt.pbtTrials
import me.tbsten.katachi.test.pbt.pick
import me.tbsten.katachi.test.pbt.without

/*
 * What the diff report writes about a change, for file names, commit subjects and authors the
 * change's author picked at random: the report quotes them and never lets them change its own
 * structure. A copied `git diff` command gets the file name back as one shell word, a Markdown row
 * keeps its three cells, and the HTML page keeps its own elements however the names try to close or
 * open one.
 */

private val PIECES: List<String> = listOf(
    "a", "src", "Foo.kt", "日本", "😀", " ", "'", "\"", "`", "``", "\\", "|", "\\|", "$", "\$(id)", "!", "*", "?", "~", "#", ";", "&",
    "&amp;", "<", ">", "</script>", "<!--", "<x-pbt>", "{{DATA}}", "{{ROWS}}", "\n", "\r", "\t", "\u2028", "-", "--",
)

private fun Random.word(): String = List(nextInt(1, 5)) { pick(PIECES) }.joinToString("")

private fun Random.path(): String = List(nextInt(1, 4)) { word() }.joinToString("/")

private fun Random.change(): FileChange {
    val status = pick(ChangeStatus.entries)
    return FileChange(
        path = path(),
        previousPath = if (status == ChangeStatus.Renamed) path() else null,
        added = nextInt(0, 50),
        deleted = nextInt(0, 50),
        binary = nextInt(5) == 0,
        status = status,
    )
}

private fun Random.changes(): List<FileChange> = List(nextInt(1, 6)) { change() }.distinctBy { it.path }

private fun range(github: GitHubRepository?): DiffRange = DiffRange(
    before = "main",
    after = "HEAD",
    separator = "..",
    beforeSha = "1".repeat(40),
    afterSha = "2".repeat(40),
    pullRequest = null,
    github = github,
    repositoryPrefix = "",
)

private fun modelOf(changes: List<FileChange>): DiffReportModel =
    DiffReportModel(range = range(GitHubRepository("example", "app")), nodes = emptyList(), unassigned = changes, changes = changes)

private fun FileChange.describe(): String = "path=${path.quoted()} previous=${previousPath?.quoted()} status=$status"

private fun String.quoted(): String = "\"" + replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

// region readers

/** The words a POSIX shell splits [command] into, for the quoting `shellQuote` writes: bare words and '...'. */
private fun shellWords(command: String): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()
    var inWord = false
    var index = 0
    while (index < command.length) {
        val char = command[index]
        when {
            char == '\'' -> {
                val close = command.indexOf('\'', index + 1)
                require(close >= 0) { "an unclosed quote in $command" }
                current.append(command, index + 1, close)
                inWord = true
                index = close + 1
            }

            char == '\\' -> {
                current.append(command[index + 1])
                inWord = true
                index += 2
            }

            char == ' ' -> {
                if (inWord) words += current.toString()
                current.clear()
                inWord = false
                index++
            }

            else -> {
                require(char !in "\"\$`!*?~#;&|<>()\n\t") { "a character the shell would read at ${command.quoted()}: $char" }
                current.append(char)
                inWord = true
                index++
            }
        }
    }
    if (inWord) words += current.toString()
    return words
}

/** The cells of a GitHub table row: split at every `|` not escaped with a `\`, the outer two dropped. */
private fun tableCells(row: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < row.length) {
        val char = row[index]
        if (char == '\\' && index + 1 < row.length && row[index + 1] == '|') {
            current.append('|')
            index += 2
        } else if (char == '|') {
            cells += current.toString()
            current.clear()
            index++
        } else {
            current.append(char)
            index++
        }
    }
    cells += current.toString()
    return cells.drop(1).dropLast(1)
}

/** What a code span shows, with the `|` escapes already undone by [tableCells]: the text between the fences, one padding space stripped each side. */
private fun codeSpanText(cell: String): String {
    val trimmed = cell.trim()
    val fence = trimmed.takeWhile { it == '`' }
    require(fence.isNotEmpty() && trimmed.endsWith(fence)) { "not a code span: $cell" }
    val inside = trimmed.substring(fence.length, trimmed.length - fence.length)
    return if (inside.length >= 2 && inside.startsWith(' ') && inside.endsWith(' ') && inside.isNotBlank()) inside.substring(1, inside.length - 1) else inside
}

private fun Regex.count(text: String): Int = findAll(text).count()

/** [this] as a double-quoted HTML attribute holds it. */
private fun String.attributeText(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

// endregion

class DiffOutputPropertySpec : FreeSpec({
    "シェル用に引用したパスは、シェルが 1 語として元の文字列に戻す" {
        checkTrials(
            "shellQuote",
            trials = pbtTrials(3000),
            seed = pbtSeed(20261130L),
            generate = { random -> random.path() },
            shrinks = { text -> text.indices.map { text.removeRange(it, it + 1) }.filter { it.isNotEmpty() } },
            describe = { it.quoted() },
        ) { path ->
            shellWords(shellQuote(path)) shouldBe listOf(path)
        }
    }

    "git diff のコマンドは、変更のパス（名前の変更は古いパスも）を -- の後の語としてそのまま渡す" {
        checkTrials(
            "gitDiffCommand",
            trials = pbtTrials(2000),
            seed = pbtSeed(20261131L),
            generate = { random -> random.change() },
            describe = { it.describe() },
        ) { change ->
            val words = shellWords(range(github = null).gitDiffCommand(change))
            val paths = words.drop(words.indexOf("--") + 1)
            val expected = when {
                change.status == ChangeStatus.Untracked -> listOf("/dev/null", change.path)
                else -> listOfNotNull(change.previousPath, change.path)
            }
            paths shouldBe expected
        }
    }

    "Markdown の表の行は、どんなファイル名でも 3 列のままで、1 列目にファイル名が出る" {
        checkTrials(
            "Markdown rows",
            trials = pbtTrials(800),
            seed = pbtSeed(20261201L),
            generate = { random -> random.changes() },
            shrinks = { changes -> changes.indices.map { changes.without(it) }.filter { it.isNotEmpty() } },
            describe = { changes -> changes.joinToString("\n") { "  " + it.describe() } },
        ) { changes ->
            val names = MarkdownFileNames("diff-report.md")
            val listing = renderDiffMarkdown(modelOf(changes), names).getValue(names.filesInNoRole)
            val rows = listing.lines().filter { it.startsWith("| `") }
            withClue(listing) {
                rows.size shouldBe changes.size
                for (row in rows) withClue(row) { tableCells(row).size shouldBe 3 }
                val shown = rows.map { codeSpanText(tableCells(it).first().substringBeforeLast(" _(")) }.toSet()
                shown shouldBe changes.map { it.path.replace('\r', ' ').replace('\n', ' ') }.toSet()
            }
        }
    }

    "前後が空白のファイル名も、Markdown の表に空白ごと出る（上の性質が見つけた例）" {
        val change = FileChange(" notes.txt ", previousPath = null, added = 1, deleted = 0, binary = false, status = ChangeStatus.Added)
        val names = MarkdownFileNames("diff-report.md")
        val row = renderDiffMarkdown(modelOf(listOf(change)), names).getValue(names.filesInNoRole).lines().single { it.startsWith("| `") }
        codeSpanText(tableCells(row).first().substringBeforeLast(" _(")) shouldBe " notes.txt "
    }

    "HTML のレポートは、どんなファイル名・コミットの件名でも自分の要素の数が変わらず、埋めたデータは元の文字列に戻る" {
        val baseline = renderDiffHtml(modelOf(emptyList()), CommitHistory(emptyList(), omitted = 0))
        val scriptOpen = Regex("<script")
        val scriptClose = Regex("</script")
        checkTrials(
            "HTML structure",
            trials = pbtTrials(500),
            seed = pbtSeed(20261202L),
            generate = { random ->
                val changes = random.changes()
                val commits = List(random.nextInt(0, 3)) { index ->
                    CommitEntry(
                        sha = "$index".repeat(40),
                        short = "$index".repeat(7),
                        subject = random.word(),
                        author = random.word(),
                        date = "2026-11-21T00:00:00Z",
                        changes = changes.filter { random.nextBoolean() },
                    )
                }
                changes to commits
            },
            describe = { (changes, commits) ->
                changes.joinToString("\n") { "  " + it.describe() } + "\n" + commits.joinToString("\n") { "  commit ${it.subject.quoted()} by ${it.author.quoted()}" }
            },
        ) { (changes, commits) ->
            val html = renderDiffHtml(modelOf(changes), CommitHistory(commits, omitted = 0))
            scriptOpen.count(html) shouldBe scriptOpen.count(baseline)
            scriptClose.count(html) shouldBe scriptClose.count(baseline)
            html shouldNotContain "<x-pbt"
            // Each name is there as an attribute's text, whatever placeholder it spells (`{{ROWS}}`, `{{DATA}}`).
            for (change in changes) html shouldContain "data-full=\"${change.path.attributeText()}\""

            val data = html.substringAfter("<script type=\"application/json\" id=\"diff-data\">").substringBefore("</script>")
            val root = JsonReader(data, "diff-data").readDocument() as JsonNode.Obj
            val files = root.members.first { it.first == "files" }.second as JsonNode.Obj
            files.members.map { it.first }.toSet() shouldBe commits.flatMap { c -> c.changes.map { it.path } }.toSet()
            val subjects = (root.members.first { it.first == "commits" }.second as JsonNode.Arr).items.map { item ->
                ((item as JsonNode.Obj).members.first { it.first == "subject" }.second as JsonNode.Str).value
            }
            subjects shouldBe commits.map { it.subject }
        }
    }
})
