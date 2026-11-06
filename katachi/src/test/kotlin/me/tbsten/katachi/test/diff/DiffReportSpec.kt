package me.tbsten.katachi.test.diff

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.diff.DiffReport
import me.tbsten.katachi.diff.KatachiDiffReportNothingToWriteException
import me.tbsten.katachi.diff.KatachiInvalidDefinitionDiffWeightException
import me.tbsten.katachi.diff.KatachiInvalidDiffWeightException
import me.tbsten.katachi.diff.diffWeight
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.processor.internal.process

@OptIn(ExperimentalKatachiApi::class)
class DiffReportSpec : FreeSpec({
    val arch = architecture {
        files = wholeTree()
        "Presentation".group {
            "ViewModel".role { layout { "app" / "**" / "*ViewModel.kt".file() } }
            "Screen".role { layout { "app" / "**" / "*Screen.kt".file() } }
        }
        "Domain".group {
            "UseCase".role { layout { "domain" / "*UseCase.kt".file() } }
        }
        "Root".role { layout { "gradlew".file(); ".gitignore".file() } }
    }

    "変更されたファイルが role ごとに振り分けられ、変更のない group は閉じた側に出る" {
        withRepository { repo ->
            repo.write("app/home/HomeViewModel.kt", "class HomeViewModel\n")
            repo.write("app/home/HomeScreen.kt", "fun HomeScreen() {}\n")
            repo.write("domain/GetUserUseCase.kt", "class GetUserUseCase\n")
            repo.commit("initial")
            repo.write("app/home/HomeViewModel.kt", "class HomeViewModel {\n    val a = 1\n    val b = 2\n}\n")
            repo.write("app/mypage/MyPageViewModel.kt", "class MyPageViewModel\n")
            repo.write("notes.txt", "no role\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val markdown = allMarkdown(out)
            markdown shouldContain "Presentation.ViewModel"
            markdown shouldContain "`…/home/HomeViewModel.kt`"
            markdown shouldContain "`…/mypage/MyPageViewModel.kt` _(added)_"
            // Every unchanged role once, under its groups, unchanged roles of a changed group included.
            markdown shouldContain "<details><summary>Unchanged (3 roles)</summary>\n\n" +
                "- Root\n- **Presentation**\n  - Screen\n- **Domain**\n  - UseCase\n\n</details>"
            markdown shouldContain "<b>No role</b> 1 file"
            markdown shouldNotContain "Presentation.Screen</b>"
            // An unchanged role inside a changed group is left out of the table.
            markdown shouldNotContain "└ Screen"
            val html = out.resolve("diff-report.html").readText()
            html shouldContain "HomeViewModel.kt"
            // Like the Markdown: the table holds only what changed, the rest is a folded role tree.
            html shouldNotContain "data-changed=\"false\""
            html shouldNotContain "show unchanged"
            // Rows carry their qualified name, so a link with the view in its hash survives a regenerated report.
            html shouldContain "data-key=\"Presentation.ViewModel\""
            html shouldContain "data-key=\"no-role\""
            // Every row and section has a link button that copies an anchor to it.
            html shouldContain "data-key=\"file:app/home/HomeViewModel.kt\""
            html shouldContain "data-anchor=\"file:app/home/HomeViewModel.kt\""
            html shouldContain "data-anchor=\"Presentation.ViewModel\""
            html shouldContain "data-anchor=\"summary\""
            html shouldContain "data-anchor=\"unchanged\""
            html shouldContain "data-action=\"copy-link\""
            // The toolbar filters rows by path or role, or down to the warnings.
            html shouldContain "data-control=\"search\""
            html shouldContain "data-control=\"warnings\""
            // A flex td stops being a table cell, and the row's background and borders come apart from it.
            html shouldNotContain "td.links { display: flex"
            // The no-role group is kept last whatever the sort.
            html shouldContain "isNoRole(x) - isNoRole(y)"
            html shouldContain "<span class=\"title\">Unchanged</span><span class=\"count\" data-stat=\"unchanged\">3 roles</span>"
            // Changed roles are in the tree too, hidden, so a narrower commit selection can show them.
            html shouldContain "<div class=\"unchanged-grid\"><div data-column=\"Root\"><div class=\"group-name\" data-key=\"Root\">Root</div></div>" +
                "<div data-column=\"Presentation\"><div class=\"group-name\" data-key=\"Presentation\">Presentation</div>" +
                "<ul><li class=\"changed\" data-key=\"Presentation.ViewModel\">└ ViewModel</li>" +
                "<li data-key=\"Presentation.Screen\">└ Screen</li></ul></div>" +
                "<div data-column=\"Domain\"><div class=\"group-name\" data-key=\"Domain\">Domain</div>" +
                "<ul><li data-key=\"Domain.UseCase\">└ UseCase</li></ul></div></div>"
        }
    }

    "コピーできる git diff のコマンドは ref ではなく SHA で固定される" {
        withRepository { repo ->
            repo.write("domain/GetUserUseCase.kt", "class GetUserUseCase\n")
            repo.commit("initial")
            repo.write("domain/GetUserUseCase.kt", "class GetUserUseCase {\n}\n")
            repo.commit("change")
            val before = repo.git("rev-parse", "HEAD~1").trim()
            val after = repo.git("rev-parse", "HEAD").trim()

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val markdown = allMarkdown(out)
            markdown shouldContain "`git diff $before..$after -- domain/GetUserUseCase.kt`"
            markdown shouldNotContain "[!WARNING]"
            out.resolve("diff-report.html").readText() shouldNotContain "data-key=\"no-role\""
            markdown shouldNotContain "git diff HEAD"
            out.resolve("diff-report.html").readText() shouldContain "git diff $before..$after -- domain/GetUserUseCase.kt"
        }
    }

    "作業ツリーと比べるときも before 側は SHA で固定される" {
        withRepository { repo ->
            repo.write("domain/GetUserUseCase.kt", "class GetUserUseCase\n")
            repo.commit("initial")
            repo.write("domain/GetUserUseCase.kt", "class GetUserUseCase {\n}\n")
            val before = repo.git("rev-parse", "HEAD").trim()

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            allMarkdown(out) shouldContain "`git diff $before -- domain/GetUserUseCase.kt`"
        }
    }

    "after を空にすると作業ツリーと比べ、未追跡のファイルも数える" {
        withRepository { repo ->
            repo.write("domain/GetUserUseCase.kt", "class GetUserUseCase\n")
            repo.commit("initial")
            repo.write("domain/SaveUserUseCase.kt", "class SaveUserUseCase\nfun x() {}\n")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            allMarkdown(out) shouldContain "`…/SaveUserUseCase.kt` _(untracked)_ | +2&nbsp;-0"
            // git diff never shows an untracked file, so the command compares it with an empty file.
            allMarkdown(out) shouldContain
                "`git diff --no-index -- /dev/null domain/SaveUserUseCase.kt`"
        }
    }

    "どの role にも当たらない変更だけなら、空の表ではなくその旨と role の無いファイルを開いて出す" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("notes.txt", "no role\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val markdown = allMarkdown(out)
            markdown shouldContain "0 of 4 roles touched, 1 file in no role."
            markdown shouldContain "No role was touched by this change."
            // A file no role allows fails the architecture test, so both outputs warn about it.
            markdown shouldContain "> [!WARNING]\n> 1 file is in no role: no layout { } allows it, " +
                "so the architecture test will fail on it."
            out.resolve("diff-report.html").readText() shouldContain
                "1 file no layout { } allows. The architecture test will fail on it."
            // The table still ends at the file, under the (no role) node.
            markdown shouldContain "| **(no role)** | | +1&nbsp;-0 | 1 |\n| &nbsp;&nbsp;└ `notes.txt` _(added)_ | 🟥🟥🟥 | +1&nbsp;-0 | |"
            out.resolve("diff-report.files-by-role.md").exists() shouldBe false
            markdown shouldContain "# Files in no role: HEAD~1"
            markdown shouldContain "<details open><summary><b>No role</b> 1 file</summary>"
            out.resolve("diff-report.html").readText() shouldNotContain "data-collapsed-default"
        }
    }

    "プロジェクトがリポジトリの一部なら、コマンドのパスはリポジトリの root から書く" {
        withRepository { repo ->
            repo.write("proj/gradlew", "")
            repo.write("proj/domain/GetUserUseCase.kt", "class GetUserUseCase\n")
            repo.commit("initial")
            repo.write("proj/domain/GetUserUseCase.kt", "class GetUserUseCase {\n}\n")
            repo.commit("change")
            val before = repo.git("rev-parse", "HEAD~1").trim()
            val after = repo.git("rev-parse", "HEAD").trim()

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir.resolve("proj")),
            ).getOrThrow()

            val markdown = allMarkdown(out)
            markdown shouldContain "`git diff $before..$after -- proj/domain/GetUserUseCase.kt`"
            markdown shouldContain "# Architecture diff report: HEAD~1 (${before.take(7)})..HEAD (${after.take(7)})"
        }
    }

    "maxFiles を超えると一覧だけを差分の多いファイルに絞り、要約の数は全ファイルで数える" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("app/home/HomeViewModel.kt", "a\nb\nc\nd\n")
            repo.write("app/mypage/MyPageViewModel.kt", "a\nb\n")
            repo.write("app/other/OtherViewModel.kt", "a\n")
            repo.write("notes.txt", "a\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path, maxFiles = 2),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val markdown = allMarkdown(out)
            markdown shouldContain "4 files changed, +8 -0. 1 of 4 roles touched, 1 file in no role."
            markdown shouldContain "Showing 2 of 4 files (maxFiles=2). Raise maxFiles to see all."
            markdown shouldContain "└ ViewModel | 🟥🟥🟥 | +7&nbsp;-0 | 3 |"
            markdown shouldContain "<b>Presentation.ViewModel</b> 🟥🟥🟥 3 files, +7 -0"
            markdown shouldContain "`…/home/HomeViewModel.kt`"
            markdown shouldContain "`…/mypage/MyPageViewModel.kt`"
            markdown shouldNotContain "OtherViewModel.kt"
            markdown shouldNotContain "`notes.txt`"
            // Once under the role and once under (no role), in the summary table and in each listing.
            markdown.split("…and 1 more file").size shouldBe 5
            val html = out.resolve("diff-report.html").readText()
            html shouldContain "Showing <span data-stat=\"shown\">2</span> of <span data-stat=\"limit-total\">4</span> files (maxFiles=2)"
            // Every file has a row so a commit selection can re-rank them; the ones cut start hidden.
            html shouldContain "class=\"file limited\""
            html shouldContain "data-key=\"file:app/other/OtherViewModel.kt\""
            html shouldContain "…and 1 more file"
        }
    }

    "maxFiles が 0 なら全ファイルを出す" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("app/home/HomeViewModel.kt", "a\nb\n")
            repo.write("app/mypage/MyPageViewModel.kt", "a\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path, maxFiles = 0),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val markdown = allMarkdown(out)
            markdown shouldContain "HomeViewModel.kt"
            markdown shouldContain "MyPageViewModel.kt"
            markdown shouldNotContain "Showing"
            markdown shouldNotContain "more file"
        }
    }

    "定義の diffWeight が効き、group の値は role が自分で書かない限り引き継がれる" {
        val weighted = architecture {
            files = wholeTree()
            "Presentation".group {
                diffWeight = 3.0
                "ViewModel".role { layout { "app" / "**" / "*ViewModel.kt".file() } }
                "Screen".role {
                    diffWeight = 10.0
                    layout { "app" / "**" / "*Screen.kt".file() }
                }
            }
            "Domain".group {
                "UseCase".role { layout { "domain" / "*UseCase.kt".file() } }
            }
            "Root".role { layout { "gradlew".file(); ".gitignore".file() } }
        }
        withRepository { repo ->
            repo.commit("initial")
            repo.write("app/home/HomeViewModel.kt", "a\n")
            repo.write("app/home/HomeScreen.kt", "a\n")
            repo.write("domain/GetUserUseCase.kt", "a\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            weighted.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val html = out.resolve("diff-report.html").readText()
            html shouldContain "ViewModel <span class=\"weight\">×3</span>"
            html shouldContain "Screen <span class=\"weight\">×10</span>"
            html shouldContain "data-weighted=\"10.0\""
            html shouldNotContain "UseCase <span class=\"weight\">"

            // An entry passed to the report wins over the definition, even on a less specific name.
            weighted.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path, weights = listOf("Presentation=0.5")),
                RealFileSystem(repo.dir),
            ).getOrThrow()
            val overridden = out.resolve("diff-report.html").readText()
            overridden shouldContain "ViewModel <span class=\"weight\">×0.5</span>"
            overridden shouldContain "Screen <span class=\"weight\">×0.5</span>"
        }
    }

    "定義の diffWeight が負なら、書いた場所を挙げて失敗する" {
        val negative = architecture {
            files = wholeTree()
            "Domain".group {
                "UseCase".role {
                    diffWeight = -1.0
                    layout { "domain" / "*UseCase.kt".file() }
                }
            }
        }
        withRepository { repo ->
            repo.commit("initial")
            val failure = negative.process(
                DiffReport,
                DiffReport.Args(markdownOutputFile = repo.dir.resolve("out/diff-report.md").path),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure as? KatachiInvalidDefinitionDiffWeightException)?.qualifiedName shouldBe "Domain.UseCase"
        }
    }

    "Markdown は要約・role ごとのファイル・role の無いファイルの 3 つに分け、要約から相対リンクで辿れる" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("app/home/HomeViewModel.kt", "a\n")
            repo.write("notes.txt", "a\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val summary = out.resolve("diff-report.md").readText()
            summary shouldContain "| Role | Importance | Diff&nbsp;lines | Files |"
            summary shouldContain "GitHub links and `git diff` commands per file: [Files by role](diff-report.files-by-role.md) · " +
                "[Files in no role](diff-report.files-in-no-role.md)"
            // The table goes down to the files, like the HTML; the commands stay in the listings.
            summary shouldContain "| &nbsp;&nbsp;└ ViewModel | 🟥🟥🟥 | +1&nbsp;-0 | 1 |\n" +
                "| &nbsp;&nbsp;&nbsp;&nbsp;└ `…/home/HomeViewModel.kt` _(added)_ | 🟥🟥🟥 | +1&nbsp;-0 | |"
            summary shouldContain "| **(no role)** | | +1&nbsp;-0 | 1 |\n| &nbsp;&nbsp;└ `notes.txt` _(added)_ | 🟥🟥🟥 | +1&nbsp;-0 | |"
            summary shouldNotContain "git diff "
            val byRole = out.resolve("diff-report.files-by-role.md").readText()
            byRole shouldContain "# Files by role: HEAD~1"
            byRole shouldContain "Back to the [summary](diff-report.md)."
            byRole shouldContain "_Run the `git diff` commands from the repository root._"
            byRole shouldContain "`…/home/HomeViewModel.kt`"
            byRole shouldNotContain "notes.txt"
            val noRole = out.resolve("diff-report.files-in-no-role.md").readText()
            noRole shouldContain "_Run the `git diff` commands from the repository root._"
            noRole shouldContain "`notes.txt`"

            // A later run with nothing in no role removes the listing the earlier run left.
            repo.git("rm", "-q", "notes.txt")
            repo.commit("drop notes")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~2", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()
            out.resolve("diff-report.files-in-no-role.md").exists() shouldBe false
            out.resolve("diff-report.md").readText() shouldNotContain "Files in no role"
        }
    }

    "Markdown の出力先の名前から一覧のファイル名を決め、html=false なら HTML は書かない" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("app/home/HomeViewModel.kt", "a\n")
            repo.write("notes.txt", "a\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", html = false, markdownOutputFile = out.resolve("review.md").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            out.list().orEmpty().sorted() shouldBe listOf("review.files-by-role.md", "review.files-in-no-role.md", "review.md")
            out.resolve("review.md").readText() shouldContain "[Files by role](review.files-by-role.md)"
            out.resolve("review.files-by-role.md").readText() shouldContain "Back to the [summary](review.md)."
        }
    }

    "markdown も html も false なら、何も書かずに失敗する" {
        withRepository { repo ->
            repo.commit("initial")
            val failure = arch.process(
                DiffReport,
                DiffReport.Args(markdown = false, html = false),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure is KatachiDiffReportNothingToWriteException) shouldBe true
        }
    }

    "HTML にはコミットごとの行数を埋め込み、作業ツリーまでなら作業ツリーも 1 つのコミットとして並べる" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("app/home/HomeViewModel.kt", "a\n")
            repo.commit("first")
            repo.write("app/home/HomeViewModel.kt", "a\nb\nc\n")
            repo.commit("second")
            repo.write("domain/GetUserUseCase.kt", "a\nb\n")
            repo.commit("third")
            repo.write("notes.txt", "a\n")
            val shorts = repo.git("log", "--reverse", "--format=%h", "HEAD~3..HEAD").lines().filter { it.isNotBlank() }

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~3", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val html = out.resolve("diff-report.html").readText()
            shorts.size shouldBe 3
            for ((index, short) in shorts.withIndex()) {
                html shouldContain "\"short\":\"$short\""
                html shouldContain "data-commit=\"$short\""
                index shouldBe shorts.indexOf(short)
            }
            html shouldContain "\"subject\":\"second\""
            // Per commit, per file: [commit index, added, deleted, binary, status].
            html shouldContain "\"app/home/HomeViewModel.kt\":[[0,1,0,0,\"A\"],[1,2,0,0,\"M\"]]"
            html shouldContain "\"domain/GetUserUseCase.kt\":[[2,2,0,0,\"A\"]]"
            // The uncommitted changes come last, as their own entry.
            html shouldContain "\"short\":\"worktree\""
            html shouldContain "\"notes.txt\":[[3,1,0,0,\"U\"]]"
            html shouldContain "<span data-stat=\"selected\">4</span> of 4"
            // Every commit starts selected, so the panel starts closed.
            html shouldContain "<details class=\"commits\"><summary>"
        }
    }

    "存在しない名前の重みは定義の名前を挙げて失敗する" {
        withRepository { repo ->
            repo.commit("initial")
            val failure = arch.process(
                DiffReport,
                DiffReport.Args(weights = listOf("Nowhere=2"), markdownOutputFile = repo.dir.resolve("out/diff-report.md").path),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure is KatachiInvalidDiffWeightException) shouldBe true
        }
    }
})

// The summary and the file listings together, for assertions that do not care which file a line is in.
private fun allMarkdown(out: File): String =
    listOf("diff-report.md", "diff-report.files-by-role.md", "diff-report.files-in-no-role.md")
        .map(out::resolve)
        .filter(File::exists)
        .joinToString("\n") { it.readText() }

private class Repository(val dir: File) {
    fun write(path: String, content: String) {
        dir.resolve(path).apply { parentFile.mkdirs() }.writeText(content)
    }

    fun commit(message: String) {
        git("add", "-A")
        git("-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-q", "--allow-empty", "-m", message)
    }

    fun git(vararg args: String): String {
        val process = ProcessBuilder(listOf("git", *args)).directory(dir).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        if (process.waitFor() != 0) throw IllegalStateException("git ${args.joinToString(" ")} failed: $output")
        return output
    }
}

private inline fun withRepository(block: (Repository) -> Unit) {
    val dir = Files.createTempDirectory("katachi-diff").toFile()
    try {
        val repo = Repository(dir)
        repo.git("init", "-q")
        repo.write("gradlew", "")
        repo.write(".gitignore", "out/\n")
        block(repo)
    } finally {
        dir.deleteRecursively()
    }
}
