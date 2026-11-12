package me.tbsten.katachi.test.diff

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.diff.internal.ChangeStatus
import me.tbsten.katachi.diff.internal.DiffRange
import me.tbsten.katachi.diff.internal.DiffReportModel
import me.tbsten.katachi.diff.internal.FileChange
import me.tbsten.katachi.diff.internal.GitHubRepository
import me.tbsten.katachi.diff.internal.MarkdownFileNames
import me.tbsten.katachi.diff.internal.PullRequest
import me.tbsten.katachi.diff.internal.renderDiffMarkdown

// `pr=` goes through `gh pr view`, which a test repository cannot answer, so the range is built by hand.
class DiffReportPullRequestMarkdownSpec : FreeSpec({
    val base = "1".repeat(40)
    val head = "2".repeat(40)
    val noRole = FileChange("notes.txt", previousPath = null, added = 1, deleted = 0, binary = false, status = ChangeStatus.Added)
    val ignored = FileChange("docs/guide.md", previousPath = null, added = 2, deleted = 1, binary = false, status = ChangeStatus.Modified)

    fun markdown(pullRequest: PullRequest?, github: GitHubRepository?): Map<String, String> = renderDiffMarkdown(
        DiffReportModel(
            range = DiffRange(
                before = base,
                after = head,
                separator = if (pullRequest == null) ".." else "...",
                beforeSha = base,
                afterSha = head,
                pullRequest = pullRequest,
                github = github,
                repositoryPrefix = "",
            ),
            nodes = emptyList(),
            unassigned = listOf(noRole),
            changes = listOf(noRole, ignored),
            ignored = listOf(ignored),
        ),
        MarkdownFileNames("diff-report.md"),
    )

    val repository = GitHubRepository(owner = "example", name = "app")
    val pullRequest = PullRequest(number = 7, url = "https://github.com/example/app/pull/7")

    "PR のレポートは GitHub のリンクだけを出し、git diff のコマンドは出さない" {
        val files = markdown(pullRequest, repository)

        files.getValue("diff-report.md") shouldContain
            "GitHub links per file: [Files by role](diff-report.files-by-role.md) · [Files in no role](diff-report.files-in-no-role.md)"
        for (listing in listOf("diff-report.files-by-role.md", "diff-report.files-in-no-role.md")) {
            files.getValue(listing) shouldContain "| [GitHub](https://github.com/example/app/pull/7/files#diff-"
            files.getValue(listing) shouldNotContain "git diff"
        }
    }

    "before / after のレポートは、GitHub のリンクに git diff のコマンドを添える" {
        val files = markdown(pullRequest = null, repository)

        files.getValue("diff-report.md") shouldContain "GitHub links and `git diff` commands per file: "
        files.getValue("diff-report.files-in-no-role.md") shouldContain "_Run the `git diff` commands from the repository root._"
        files.getValue("diff-report.files-in-no-role.md") shouldContain
            "| [GitHub](https://github.com/example/app/compare/$base..$head#diff-"
        files.getValue("diff-report.files-in-no-role.md") shouldContain "`git diff $base..$head -- notes.txt` |"
    }

    "GitHub のリンクを作れない PR のレポートは、git diff のコマンドを残す" {
        val files = markdown(pullRequest, github = null)

        files.getValue("diff-report.files-in-no-role.md") shouldContain "| `git diff $base...$head -- notes.txt` |"
    }
})
