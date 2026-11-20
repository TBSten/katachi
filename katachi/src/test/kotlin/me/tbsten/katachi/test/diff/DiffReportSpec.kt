package me.tbsten.katachi.test.diff

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.KatachiInvalidBaselineFileException
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.BaselineRuns
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.diff.DiffReport
import me.tbsten.katachi.diff.KatachiDiffGitException
import me.tbsten.katachi.diff.KatachiDiffPullRequestException
import me.tbsten.katachi.diff.KatachiDiffReportIoException
import me.tbsten.katachi.diff.KatachiDiffReportNothingToWriteException
import me.tbsten.katachi.diff.KatachiInvalidDefinitionDiffWeightException
import me.tbsten.katachi.diff.KatachiInvalidDiffWeightException
import me.tbsten.katachi.diff.KatachiInvalidFullImportanceLinesException
import me.tbsten.katachi.diff.diffWeight
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.processor.internal.process
import tools.jackson.databind.json.JsonMapper

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
    val ignoringArch = architecture {
        files = wholeTree()
        "UseCase".role { layout { "domain" / "*UseCase.kt".file() } }
        "Root".role {
            layout {
                "gradlew".file()
                ".gitignore".file()
                "sample" { "jvm".ignore() }
            }
        }
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
            markdown shouldContain "| **Presentation** |"
            markdown shouldContain "└ ViewModel |"
            markdown shouldContain "`…/home/HomeViewModel.kt`"
            markdown shouldContain "`…/mypage/MyPageViewModel.kt` _(added)_"
            // Every unchanged role once, under its groups, unchanged roles of a changed group included.
            markdown shouldContain "<details><summary>Unchanged (3 roles)</summary>\n\n" +
                "- Root\n- **Presentation**\n  - Screen\n- **Domain**\n  - UseCase\n\n</details>"
            markdown shouldContain "<b>No role</b> 1 file"
            // The files of a role are in the summary's table alone, with no listing of them.
            markdown shouldNotContain "Files by role"
            markdown shouldNotContain "<b>Presentation.ViewModel</b>"
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
            // The Markdown lists no role's files, so the commands for them are in the HTML.
            markdown shouldNotContain "git diff"
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

            out.resolve("diff-report.html").readText() shouldContain "git diff $before -- domain/GetUserUseCase.kt"
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

            allMarkdown(out) shouldContain "`…/SaveUserUseCase.kt` _(untracked)_ | ${squares("🟨", 1)} | +2&nbsp;-0"
            // git diff never shows an untracked file, so the command compares it with an empty file.
            out.resolve("diff-report.html").readText() shouldContain
                "git diff --no-index -- /dev/null domain/SaveUserUseCase.kt"
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
            markdown shouldContain "| **(no role)** | | +1&nbsp;-0 | 1 |\n| &nbsp;&nbsp;└ `notes.txt` _(added)_ | ${squares("🟨", 1)} | +1&nbsp;-0 | |"
            out.resolve("diff-report.files-by-role.md").exists() shouldBe false
            markdown shouldContain "# Files in no role: HEAD~1"
            markdown shouldContain "<details open><summary><b>No role</b> 1 file</summary>"
            out.resolve("diff-report.html").readText() shouldNotContain "data-collapsed-default"
            out.resolve("diff-report.html").readText() shouldContain "<div class=\"ignored-stat\" hidden>"
        }
    }

    "ignore() したディレクトリの変更は no role と数えず、警告なしの ignored に分けて出す" {
        withRepository { repo ->
            repo.write("domain/GetUserUseCase.kt", "class GetUserUseCase\n")
            repo.commit("initial")
            repo.write("domain/GetUserUseCase.kt", "class GetUserUseCase {\n}\n")
            repo.write("sample/jvm/src/Foo.kt", "class Foo\n")
            repo.write("notes.txt", "no role\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            ignoringArch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val markdown = allMarkdown(out)
            markdown shouldContain "3 files changed, +4 -1. 1 of 2 roles touched, 1 file in no role, 1 file ignored."
            // Only the file the architecture test fails on is warned about.
            markdown shouldContain "> 1 file is in no role: no layout { } allows it, so the architecture test will fail on it."
            markdown shouldContain "| **(no role)** | | +1&nbsp;-0 | 1 |\n| &nbsp;&nbsp;└ `notes.txt` _(added)_ |"
            markdown shouldContain "| **(ignored)** | | +1&nbsp;-0 | 1 |\n| &nbsp;&nbsp;└ `sample/jvm/src/Foo.kt` _(added)_ |"
            markdown shouldContain "_(ignored): Files under a directory `ignore()` leaves out of the layout. " +
                "The architecture test does not look at them._"
            out.resolve("diff-report.files-in-no-role.md").readText() shouldNotContain "sample/jvm"
            // The ignored files are listed in the summary's table; there is no listing of them apart.
            out.resolve("diff-report.files-by-role.md").exists() shouldBe false

            val html = out.resolve("diff-report.html").readText()
            html shouldContain "1 file no layout { } allows. The architecture test will fail on it."
            html shouldContain "1 file under ignore(). The architecture test does not look at it."
            html shouldContain "data-key=\"ignored\""
            html shouldContain "class=\"file ignored-file\""
            // The header's warning counts the no-role file alone.
            html shouldContain "data-stat=\"no-role\">1</span>"
            html shouldContain "<div class=\"ignored-stat\"><span class=\"num\" data-stat=\"ignored\">1</span>"
        }
    }

    "変更が ignore() したディレクトリだけなら警告を出さない" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("sample/jvm/src/Foo.kt", "class Foo\n")
            repo.commit("change")

            val out = repo.dir.resolve("out")
            ignoringArch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val markdown = allMarkdown(out)
            markdown shouldContain "0 of 2 roles touched, 1 file ignored."
            markdown shouldNotContain "[!WARNING]"
            markdown shouldNotContain "no role"
            out.resolve("diff-report.files-in-no-role.md").exists() shouldBe false
            val html = out.resolve("diff-report.html").readText()
            html shouldContain "<div class=\"warn-stat\" hidden>"
            html shouldContain "<div class=\"ignored-stat\"><span class=\"num\" data-stat=\"ignored\">1</span>"
            html shouldNotContain "data-key=\"no-role\""
            html shouldContain "data-collapsed-default=\"true\" data-files=\"1\" data-key=\"ignored\""
        }
    }

    "どこに出るかは変更後のパスで決まり、消したファイルは no role に数えない" - {
        val placing = architecture {
            files = wholeTree()
            "Domain".group {
                "UseCase".role { layout { "domain" / "*UseCase.kt".file() } }
                "Repository".role { layout { "data" / "*Repository.kt".file() } }
            }
            "Root".role { layout { "gradlew".file(); ".gitignore".file(); "generated".ignore() } }
        }

        // The file starts at `from`, the change moves it to `to` (null deletes it), and the report lists `to`
        // (or `from` when deleted) in `section`: a role's qualified name, or a key of the files no role owns.
        class Case(val name: String, val from: String, val to: String?, val section: String) {
            val noRoleFileCount: Int get() = if (section == "noRole") 1 else 0
        }
        val cases = listOf(
            Case("Role の外へ移すと no role", "domain/GetUserUseCase.kt", "misc/GetUserUseCase.kt", "noRole"),
            Case("ignore() の下へ移すと ignored", "domain/GetUserUseCase.kt", "generated/GetUserUseCase.kt", "ignored"),
            Case("ignore() の下から Role の外へ移すと no role", "generated/GetUserUseCase.kt", "misc/GetUserUseCase.kt", "noRole"),
            Case("同じ Role の中の名前の変更はその Role", "domain/GetUserUseCase.kt", "domain/FetchUserUseCase.kt", "Domain.UseCase"),
            Case("別の Role へ移すと移した先の Role", "domain/UserUseCase.kt", "data/UserRepository.kt", "Domain.Repository"),
            Case("Role の外から Role へ移すとその Role", "misc/GetUserUseCase.kt", "domain/GetUserUseCase.kt", "Domain.UseCase"),
            Case("Role の外のファイルを消すと、no role ではなく noRoleDeleted", "misc/stray.txt", null, "noRoleDeleted"),
            Case("Role のファイルを消すと、その Role", "domain/GetUserUseCase.kt", null, "Domain.UseCase"),
            Case("ignore() の下のファイルを消すと ignored", "generated/GetUserUseCase.kt", null, "ignored"),
        )
        for (case in cases) {
            case.name {
                withRepository { repo ->
                    repo.write(case.from, USE_CASE_SOURCE)
                    repo.commit("initial")
                    if (case.to == null) repo.git("rm", "-q", case.from) else repo.move(case.from, case.to)
                    repo.commit("change")

                    val report = placing.reportOn(repo)

                    val path = case.to ?: case.from
                    sectionsOf(report.json, path) shouldBe listOf(case.section)
                    report.json.at("summary", "noRoleFileCount") shouldBe case.noRoleFileCount
                    // The warning and the header figure say what the architecture test will fail on, nothing more.
                    report.markdown.contains("[!WARNING]") shouldBe (case.noRoleFileCount > 0)
                    report.html shouldContain "data-stat=\"no-role\">${case.noRoleFileCount}</span>"
                }
            }
        }
    }

    "Role のファイルをどの Role にも無い場所へ移すと、古い Role ではなく no role に数えて警告する" {
        withRepository { repo ->
            repo.write("domain/GetUserUseCase.kt", USE_CASE_SOURCE)
            repo.commit("initial")
            repo.move("domain/GetUserUseCase.kt", "misc/GetUserUseCase.kt")
            repo.commit("move GetUserUseCase out of every role")

            val report = arch.reportOn(repo)

            report.summary shouldContain "1 file changed, +0 -0. 0 of 4 roles touched, 1 file in no role."
            report.summary shouldContain "> [!WARNING]\n> 1 file is in no role: no layout { } allows it, " +
                "so the architecture test will fail on it."
            report.summary shouldContain "| **(no role)** | | +0&nbsp;-0 | 1 |\n| &nbsp;&nbsp;└ `misc/GetUserUseCase.kt` _(renamed)_ |"
            report.summary shouldNotContain "└ UseCase"
            report.json.at("summary", "noRoleFileCount") shouldBe 1
            report.json.at("summary", "changedRoles") shouldBe emptyList<Any?>()
            report.json.at("noRole", "files", 0, "previousPath") shouldBe "domain/GetUserUseCase.kt"
            report.html shouldContain "<div class=\"warn-stat\"><span class=\"num\" data-stat=\"no-role\">1</span>"
            report.html shouldContain "1 file no layout { } allows. The architecture test will fail on it."
            report.html shouldContain "class=\"file unassigned-file\""
        }
    }

    "どの Role にも無いファイルを消すだけなら、no role に数えず警告もせず、消えたファイルとして畳んで出す" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("misc/stray.txt", "x\n")
            repo.commit("add a stray file")
            repo.git("rm", "-q", "misc/stray.txt")
            repo.commit("remove the stray file")

            val report = arch.reportOn(repo)

            report.summary shouldContain "1 file changed, +0 -1. 0 of 4 roles touched, 1 file in no role deleted."
            report.markdown shouldNotContain "[!WARNING]"
            report.markdown shouldNotContain "will fail"
            report.summary shouldContain "| **(no role, deleted)** | | +0&nbsp;-1 | 1 |\n| &nbsp;&nbsp;└ `misc/stray.txt` _(deleted)_ | ${squares("🟨", 1)} | +0&nbsp;-1 | |"
            report.summary shouldContain "_(no role, deleted): Files no layout { } allows that this change deletes. " +
                "The architecture test does not look at them any more._"
            // The listing is for the files the architecture test fails on, so there is none.
            report.out.resolve("diff-report.files-in-no-role.md").exists() shouldBe false

            // Before the scripts: the page's script holds the same wording, to rewrite a row when the view changes.
            report.html.substringBefore("<script") shouldNotContain "will fail"
            report.html shouldContain "<div class=\"warn-stat\" hidden>"
            report.html shouldContain "<div class=\"removed-stat\"><span class=\"num\" data-stat=\"no-role-deleted\">1</span>"
            report.html shouldContain "1 file no layout { } allows, deleted by this change. The architecture test does not look at it any more."
            report.html shouldContain "data-collapsed-default=\"true\" data-files=\"1\" data-key=\"no-role-deleted\""
            report.html shouldContain "class=\"file removed-file\""

            report.json.at("summary", "noRoleFileCount") shouldBe 0
            report.json.at("summary", "noRoleDeletedFileCount") shouldBe 1
            report.json.at("noRole", "files") shouldBe emptyList<Any?>()
            report.json.at("noRoleDeleted", "files", 0, "path") shouldBe "misc/stray.txt"
            report.json.at("noRoleDeleted", "files", 0, "status") shouldBe "deleted"
            report.yaml shouldContain "  noRoleFileCount: 0\n  baselinedFileCount: 0\n  noRoleDeletedFileCount: 1\n"
        }
    }

    "消したファイルと残るファイルが両方 no role なら、残る方だけを数えて警告する" {
        withRepository { repo ->
            repo.write("misc/stray.txt", "x\n")
            repo.commit("initial")
            repo.git("rm", "-q", "misc/stray.txt")
            repo.write("notes.txt", "no role\n")
            repo.commit("change")

            val report = arch.reportOn(repo)

            report.summary shouldContain "2 files changed, +1 -1. 0 of 4 roles touched, 1 file in no role, 1 file in no role deleted."
            report.summary shouldContain "> 1 file is in no role: no layout { } allows it, so the architecture test will fail on it."
            report.out.resolve("diff-report.files-in-no-role.md").readText() shouldNotContain "stray.txt"
            report.html shouldContain "data-stat=\"no-role\">1</span>"
            report.json.at("summary", "noRoleFileCount") shouldBe 1
            report.json.at("noRole", "files", 0, "path") shouldBe "notes.txt"
            report.json.at("noRoleDeleted", "files", 0, "path") shouldBe "misc/stray.txt"
        }
    }

    // A project that adopted katachi with a baseline, declared the way the guide declares it.
    val baselining = architecture {
        files = wholeTree()
        baseline()
        "UseCase".role { layout { "domain" / "*UseCase.kt".file() } }
        "Screen".role { layout { "feature" / capture("feature") / "${capture("feature")}Screen.kt".file() } }
        "BaselineFile".role { layout { "katachi-baseline.json".file() } }
        "Root".role {
            layout {
                "gradlew".file()
                ".gitignore".file()
                "out".ignore()
            }
        }
    }

    "baseline が抑えている no role のファイルを触っても、no role に数えず警告もせず、baseline の段に畳んで出す" {
        withRepository { repo ->
            repo.write("domain/LegacyHelper.kt", "object LegacyHelper\n")
            baselining.recordBaseline(repo)
            repo.commit("initial")
            repo.write("domain/LegacyHelper.kt", "object LegacyHelper\n// touched\n")
            repo.commit("touch a file the baseline holds back")

            // The report has to agree with the architecture test, which fails on nothing here.
            baselining.failuresOfArchitectureTest(repo) shouldBe emptyList()
            val report = baselining.reportOn(repo)

            report.summary shouldContain "1 file changed, +1 -0. 0 of 4 roles touched, 1 file held back by the baseline."
            report.markdown shouldNotContain "[!WARNING]"
            report.markdown shouldNotContain "will fail"
            report.summary shouldContain "| **(no role, held back by the baseline)** | | +1&nbsp;-0 | 1 |\n" +
                "| &nbsp;&nbsp;└ `domain/LegacyHelper.kt` | ${squares("🟨", 1)} | +1&nbsp;-0 | |"
            report.summary shouldContain "_(no role, held back by the baseline): Files no layout { } allows that the baseline holds back. " +
                "The architecture test does not fail on them._"
            report.out.resolve("diff-report.files-in-no-role.md").exists() shouldBe false

            report.html.substringBefore("<script") shouldNotContain "will fail"
            report.html shouldContain "<div class=\"warn-stat\" hidden>"
            report.html shouldContain "<div class=\"baselined-stat\"><span class=\"num\" data-stat=\"baselined\">1</span>"
            report.html shouldContain "1 file no layout { } allows, held back by the baseline. The architecture test does not fail on it."
            report.html shouldContain "data-collapsed-default=\"true\" data-files=\"1\" data-key=\"baselined\""
            report.html shouldContain "class=\"file baselined-file\""

            report.json.at("summary", "noRoleFileCount") shouldBe 0
            report.json.at("summary", "baselinedFileCount") shouldBe 1
            report.json.at("noRole", "files") shouldBe emptyList<Any?>()
            report.json.at("baselined", "files", 0, "path") shouldBe "domain/LegacyHelper.kt"
            report.yaml shouldContain "  noRoleFileCount: 0\n  baselinedFileCount: 1\n"
        }
    }

    "baseline が記録したファイル・ディレクトリ・capture の食い違いは baseline の段に数え、記録の無い no role のファイルだけを警告する" {
        withRepository { repo ->
            repo.write("domain/LegacyHelper.kt", "object LegacyHelper\n")
            repo.write("legacy/Old.kt", "object Old\n")
            repo.write("feature/home/settingsScreen.kt", "object Settings\n")
            baselining.recordBaseline(repo)
            repo.commit("initial")
            // One entry of each kind the layout check records for a file no role allows.
            val entries = repo.dir.resolve("katachi-baseline.json").readText()
            entries shouldContain "{\"rule\": \"UnexpectedFile\", \"path\": \"domain/LegacyHelper.kt\"}"
            entries shouldContain "{\"rule\": \"UnexpectedDirectory\", \"path\": \"legacy\"}"
            entries shouldContain "{\"rule\": \"CaptureMismatch\", \"path\": \"feature/home/settingsScreen.kt\"}"
            repo.write("domain/LegacyHelper.kt", "object LegacyHelper\n// touched\n")
            // The directory's entry holds back what is added under it too: the check does not look below it.
            repo.write("legacy/sub/New.kt", "object New\n")
            repo.write("feature/home/settingsScreen.kt", "object Settings\n// touched\n")
            repo.write("domain/Stray.kt", "object Stray\n")
            repo.commit("change")

            baselining.failuresOfArchitectureTest(repo) shouldBe listOf("[UnexpectedFile] domain/Stray.kt")
            val report = baselining.reportOn(repo)

            report.summary shouldContain "4 files changed, +4 -0. 0 of 4 roles touched, 1 file in no role, 3 files held back by the baseline."
            report.summary shouldContain "> 1 file is in no role: no layout { } allows it, so the architecture test will fail on it."
            report.out.resolve("diff-report.files-in-no-role.md").readText() shouldNotContain "LegacyHelper"
            report.html shouldContain "data-stat=\"no-role\">1</span>"
            report.html shouldContain "data-stat=\"baselined\">3</span>"
            report.json.at("summary", "noRoleFileCount") shouldBe 1
            report.json.at("summary", "baselinedFileCount") shouldBe 3
            sectionsOf(report.json, "domain/Stray.kt") shouldBe listOf("noRole")
            for (path in listOf("domain/LegacyHelper.kt", "legacy/sub/New.kt", "feature/home/settingsScreen.kt")) {
                sectionsOf(report.json, path) shouldBe listOf("baselined")
            }
        }
    }

    "baseline を宣言していても、ファイルがまだ無ければ今までどおり no role に数えて警告する" {
        withRepository { repo ->
            repo.write("domain/LegacyHelper.kt", "object LegacyHelper\n")
            repo.commit("initial")
            repo.write("domain/LegacyHelper.kt", "object LegacyHelper\n// touched\n")
            repo.commit("touch")

            val report = baselining.reportOn(repo)

            report.summary shouldContain "1 file changed, +1 -0. 0 of 4 roles touched, 1 file in no role."
            report.summary shouldContain "> [!WARNING]"
            report.json.at("summary", "noRoleFileCount") shouldBe 1
            report.json.at("summary", "baselinedFileCount") shouldBe 0
        }
    }

    "baseline のファイルが読めなければ、アーキテクチャテストと同じ例外で失敗する" {
        withRepository { repo ->
            repo.write("katachi-baseline.json", "{ \"version\": 1,\n")
            repo.commit("initial")
            val failure = baselining.process(
                DiffReport,
                DiffReport.Args(markdownOutputFile = repo.dir.resolve("out/diff-report.md").path, html = false, json = false, yaml = false),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure is KatachiInvalidBaselineFileException) shouldBe true
        }
    }

    "ファイル名・ref・コミットの件名にプレースホルダと同じ綴りがあっても、HTML には文字のまま出る" {
        withRepository { repo ->
            repo.commit("initial")
            repo.git("checkout", "-q", "-b", "topic-{{DATA}}")
            val names = listOf("misc/{{DATA}}.txt", "misc/{{COMMITS}}.txt", "misc/{{ROWS}}.txt")
            for (name in names) repo.write(name, "a\n")
            // The subject would close a double-quoted attribute and add attributes of its own.
            val subject = "\" tabindex=0 autofocus onfocus=alert(document.title) x=\""
            repo.commit(subject)
            val before = repo.git("rev-parse", "HEAD~1").trim()
            val after = repo.git("rev-parse", "HEAD").trim()

            val html = arch.reportOn(repo, after = "topic-{{DATA}}").html

            for (name in names) {
                html shouldContain "data-key=\"file:$name\""
                html shouldContain "data-full=\"$name\" data-short=\"$name\">$name</code>"
            }
            html shouldContain "<title>Architecture diff: HEAD~1 (${before.take(7)})..topic-{{DATA}} (${after.take(7)})</title>"
            // Nothing but the rows is in the table: no commit list, no commit data, no attribute the report does not write.
            val rows = html.substringAfter("<tbody>").substringBefore("</tbody>")
            rows shouldNotContain "<details"
            rows shouldNotContain "\"omitted\""
            for (row in Regex("<tr [^>]*>").findAll(rows)) {
                row.value shouldNotContain "onfocus"
                row.value shouldNotContain "autofocus"
            }
            html.split("<details class=\"commits\">").size shouldBe 2
            html shouldContain "<span class=\"subject\">&quot; tabindex=0 autofocus onfocus=alert(document.title) x=&quot;</span>"
            // The commit data is in its own script element, once, and reads back as written.
            html.split("\"omitted\":").size shouldBe 2
            val data = JsonMapper().readValue(
                html.substringAfter("<script type=\"application/json\" id=\"diff-data\">").substringBefore("</script>"),
                Any::class.java,
            )
            data.at("commits", 0, "subject") shouldBe subject
            (data.at("files") as? Map<*, *>).orEmpty().keys shouldBe names.toSet()
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
            out.resolve("diff-report.html").readText() shouldContain "git diff $before..$after -- proj/domain/GetUserUseCase.kt"
            markdown shouldContain "# Architecture diff report: HEAD~1 (${before.take(7)})..HEAD (${after.take(7)})"
        }
    }

    "コマンドのパスはシェルが読み替えないよう引用し、貼り付けてもそのファイルの差分を出すだけ" {
        withRepository { repo ->
            repo.commit("initial")
            // The author of the change picks the names, and the reader pastes the command into a shell.
            repo.write("notes/x;touch PWNED;#it's here.txt", "hello\n")
            repo.commit("change")
            val before = repo.git("rev-parse", "HEAD~1").trim()
            val after = repo.git("rev-parse", "HEAD").trim()

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val command = "git diff $before..$after -- 'notes/x;touch PWNED;#it'\\''s here.txt'"
            out.resolve("diff-report.files-in-no-role.md").readText() shouldContain "`$command`"
            out.resolve("diff-report.html").readText() shouldContain "data-command=\"$command\""
            sh(repo.dir, command) shouldContain "+hello"
            repo.dir.resolve("PWNED").exists() shouldBe false
        }
    }

    "名前を変えたファイルのコマンドは古いパスも渡し、名前の変更として見せる" {
        withRepository { repo ->
            repo.write("domain/GetUserUseCase.kt", "class GetUserUseCase {\n    fun a() = 1\n    fun b() = 2\n}\n")
            repo.commit("initial")
            repo.git("mv", "domain/GetUserUseCase.kt", "domain/FetchUserUseCase.kt")
            repo.commit("rename")
            val before = repo.git("rev-parse", "HEAD~1").trim()
            val after = repo.git("rev-parse", "HEAD").trim()

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val command = "git diff -M $before..$after -- domain/GetUserUseCase.kt domain/FetchUserUseCase.kt"
            out.resolve("diff-report.html").readText() shouldContain "data-command=\"$command\""
            // Given the new path alone, git shows the whole file as added rather than as this rename.
            sh(repo.dir, command) shouldContain "rename from domain/GetUserUseCase.kt"
        }
    }

    "未追跡のバイナリは、コミット済みのものと同じく行数ではなく binary と出す" {
        withRepository { repo ->
            repo.commit("initial")
            // The start of a PNG: its newline bytes would otherwise be counted as lines.
            repo.dir.resolve("notes").mkdirs()
            repo.dir.resolve("notes/logo.png").writeBytes(byteArrayOf(-0x77, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x0A, 0x0A))

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            out.resolve("diff-report.md").readText() shouldContain "| &nbsp;&nbsp;└ `notes/logo.png` _(untracked)_ | ⬜ | binary | |"
            out.resolve("diff-report.html").readText() shouldContain "data-binary=\"true\" data-key=\"file:notes/logo.png\""
        }
    }

    "パスに | や ` があっても、Markdown の表のセルもコードの囲みも崩れない" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("notes/a|b.txt", "a\n")
            repo.write("notes/`x`.txt", "x\n")
            repo.commit("change")
            val before = repo.git("rev-parse", "HEAD~1").trim()
            val after = repo.git("rev-parse", "HEAD").trim()

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            // GitHub ends a table cell at a bare `|` even inside a code span; `\|` shows as `|`.
            val summary = out.resolve("diff-report.md").readText()
            summary shouldContain "| &nbsp;&nbsp;└ `notes/a\\|b.txt` _(added)_ |"
            summary shouldContain "| &nbsp;&nbsp;└ ``notes/`x`.txt`` _(added)_ |"
            val listing = out.resolve("diff-report.files-in-no-role.md").readText()
            listing shouldContain "| `notes/a\\|b.txt` _(added)_ | +1&nbsp;-0 | `git diff $before..$after -- 'notes/a\\|b.txt'` |"
            listing shouldContain "| ``notes/`x`.txt`` _(added)_ | +1&nbsp;-0 | ``git diff $before..$after -- 'notes/`x`.txt'`` |"
        }
    }

    "Importance は基準値に対する比を 5 段階に分け、段階の数だけ色マスを並べる" {
        withRepository { repo ->
            repo.commit("initial")
            for (lines in 1..5) repo.write("app/f$lines/F${lines}ViewModel.kt", "a\n".repeat(lines))
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path, fullImportanceLines = 5),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val summary = out.resolve("diff-report.md").readText()
            // Level n is n squares: 1 line of 5 is level 1, 5 lines of 5 is level 5.
            val expectedSquares = listOf(squares("🟨", 1), squares("🟨", 2), squares("🟧", 3), squares("🟧", 4), squares("🟥", 5))
            for ((index, expected) in expectedSquares.withIndex()) {
                val lines = index + 1
                summary shouldContain "└ `…/f$lines/F${lines}ViewModel.kt` _(added)_ | $expected | +$lines&nbsp;-0 | |"
            }
            // 15 lines are past the scale of 5: the role is cut at the top level, not drawn longer.
            summary shouldContain "└ ViewModel | ${squares("🟥", 5)} | +15&nbsp;-0 | 5 |"
            summary shouldContain "_Importance: diff lines × weight, out of 5 (${squares("🟥", 5)} at 5 or more)._"
            out.resolve("diff-report.html").readText() shouldContain "data-full-importance=\"5\""
        }
    }

    "Importance は変更全体の大きさに引きずられず、既定の基準値 400 行に対して測る" {
        withRepository { repo ->
            repo.commit("initial")
            repo.write("app/small/SmallViewModel.kt", "a\n")
            repo.write("app/large/LargeViewModel.kt", "a\n".repeat(200))
            repo.commit("change")

            val out = repo.dir.resolve("out")
            arch.process(
                DiffReport,
                DiffReport.Args(before = "HEAD~1", after = "HEAD", markdownOutputFile = out.resolve("diff-report.md").path, htmlOutputFile = out.resolve("diff-report.html").path),
                RealFileSystem(repo.dir),
            ).getOrThrow()

            val summary = out.resolve("diff-report.md").readText()
            // The largest row is 201 of 400 lines, so nothing reaches the top level.
            summary shouldContain "└ ViewModel | ${squares("🟧", 3)} | +201&nbsp;-0 | 2 |"
            summary shouldContain "└ `…/large/LargeViewModel.kt` _(added)_ | ${squares("🟧", 3)} | +200&nbsp;-0 | |"
            summary shouldContain "└ `…/small/SmallViewModel.kt` _(added)_ | ${squares("🟨", 1)} | +1&nbsp;-0 | |"
            summary shouldNotContain "🟥🟥"
            val html = out.resolve("diff-report.html").readText()
            html shouldContain "data-full-importance=\"400\""
            html shouldContain "Importance is full at 400 diff lines or more"
        }
    }

    "fullImportanceLines が 0 以下なら、git を読む前に失敗する" {
        withRepository { repo ->
            repo.commit("initial")
            val failure = arch.process(
                DiffReport,
                DiffReport.Args(before = "no-such-ref", fullImportanceLines = 0),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure is KatachiInvalidFullImportanceLinesException) shouldBe true
            failure?.message.orEmpty() shouldContain "Invalid fullImportanceLines 0"
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
            markdown shouldContain "└ ViewModel | ${squares("🟨", 1)} | +7&nbsp;-0 | 3 |"
            markdown shouldContain "`…/home/HomeViewModel.kt`"
            markdown shouldContain "`…/mypage/MyPageViewModel.kt`"
            markdown shouldNotContain "OtherViewModel.kt"
            markdown shouldNotContain "`notes.txt`"
            // Once under the role and once under (no role) in the summary table, and once in the no-role listing.
            markdown.split("…and 1 more file").size shouldBe 4
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

    "Markdown は要約と role の無いファイルの 2 つに分け、要約から相対リンクで辿れる。role ごとのファイルの一覧は書かない" {
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
            summary shouldContain "GitHub links and `git diff` commands per file: [Files in no role](diff-report.files-in-no-role.md)"
            summary shouldNotContain "Files by role"
            // The table goes down to the files, like the HTML; the commands stay in the listings.
            summary shouldContain "| &nbsp;&nbsp;└ ViewModel | ${squares("🟨", 1)} | +1&nbsp;-0 | 1 |\n" +
                "| &nbsp;&nbsp;&nbsp;&nbsp;└ `…/home/HomeViewModel.kt` _(added)_ | ${squares("🟨", 1)} | +1&nbsp;-0 | |"
            summary shouldContain "| **(no role)** | | +1&nbsp;-0 | 1 |\n| &nbsp;&nbsp;└ `notes.txt` _(added)_ | ${squares("🟨", 1)} | +1&nbsp;-0 | |"
            summary shouldNotContain "git diff "
            out.resolve("diff-report.files-by-role.md").exists() shouldBe false
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

            out.list().orEmpty().sorted() shouldBe listOf("review.files-in-no-role.md", "review.md")
            out.resolve("review.md").readText() shouldContain "[Files in no role](review.files-in-no-role.md)"
            out.resolve("review.files-in-no-role.md").readText() shouldContain "Back to the [summary](review.md)."
        }
    }

    "4 つの形式をすべて false にすると、何も書かずに失敗する" {
        withRepository { repo ->
            repo.commit("initial")
            val failure = arch.process(
                DiffReport,
                DiffReport.Args(markdown = false, html = false, json = false, yaml = false),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure is KatachiDiffReportNothingToWriteException) shouldBe true
            failure?.message.orEmpty() shouldContain "markdown=false, html=false, json=false and yaml=false"
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

    "存在しない ref は失敗した git のコマンドと、ref の取り方を言って失敗する" {
        withRepository { repo ->
            repo.commit("initial")
            val failure = arch.process(
                DiffReport,
                DiffReport.Args(before = "no-such-ref", markdownOutputFile = repo.dir.resolve("out/diff-report.md").path),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure is KatachiDiffGitException) shouldBe true
            failure as KatachiDiffGitException
            failure.command shouldContain "no-such-ref"
            failure.message.orEmpty() shouldContain "failed, so the diff report could not be built."
            failure.message.orEmpty() shouldContain "git fetch --unshallow"
        }
    }

    "PR を gh で引けなければ、gh の入れ方と ref を直接渡す方法を言って失敗する" {
        withRepository { repo ->
            // No remote: whether gh is installed or not, `gh pr view` cannot resolve the pull request.
            repo.commit("initial")
            val failure = arch.process(
                DiffReport,
                DiffReport.Args(pr = "1", markdownOutputFile = repo.dir.resolve("out/diff-report.md").path),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure is KatachiDiffPullRequestException) shouldBe true
            failure as KatachiDiffPullRequestException
            failure.pr shouldBe "1"
            failure.message.orEmpty() shouldContain "Pull request \"1\" could not be resolved with `gh pr view`."
            failure.message.orEmpty() shouldContain "--arg before=<base> --arg after=<head>"
        }
    }

    "書き出し先に書けなければ、その場所と別の出力先の渡し方を言って失敗する" {
        withRepository { repo ->
            repo.commit("initial")
            // A regular file where the output directory should be: the directory cannot be made.
            repo.write("blocker", "not a directory\n")
            val output = repo.dir.resolve("blocker/diff-report.html").path
            val failure = arch.process(
                DiffReport,
                DiffReport.Args(markdown = false, htmlOutputFile = output),
                RealFileSystem(repo.dir),
            ).exceptionOrNull()
            (failure is KatachiDiffReportIoException) shouldBe true
            failure as KatachiDiffReportIoException
            failure.outputFile shouldBe output
            failure.message.orEmpty() shouldContain "The diff report could not be written to "
            failure.message.orEmpty() shouldContain "--arg htmlOutputFile="
        }
    }
})

// The summary and the file listings together, for assertions that do not care which file a line is in.
private fun allMarkdown(out: File): String =
    listOf("diff-report.md", "diff-report.files-in-no-role.md")
        .map(out::resolve)
        .filter(File::exists)
        .joinToString("\n") { it.readText() }

// Long enough for git to pair a moved copy of it up as a rename.
private const val USE_CASE_SOURCE: String = "class UseCase {\n    fun a() = 1\n    fun b() = 2\n    fun c() = 3\n}\n"

private class Outputs(val out: File, val summary: String, val markdown: String, val html: String, val json: Any?, val yaml: String)

// Every format of one report, written to out/ and read back.
private fun Architecture.reportOn(repo: Repository, before: String = "HEAD~1", after: String = "HEAD"): Outputs {
    val out = repo.dir.resolve("out")
    process(
        DiffReport,
        DiffReport.Args(
            before = before,
            after = after,
            markdownOutputFile = out.resolve("diff-report.md").path,
            htmlOutputFile = out.resolve("diff-report.html").path,
            jsonOutputFile = out.resolve("diff-report.json").path,
            yamlOutputFile = out.resolve("diff-report.yaml").path,
        ),
        RealFileSystem(repo.dir),
    ).getOrThrow()
    return Outputs(
        out = out,
        summary = out.resolve("diff-report.md").readText(),
        markdown = allMarkdown(out),
        html = out.resolve("diff-report.html").readText(),
        json = JsonMapper().readValue(out.resolve("diff-report.json").readText(), Any::class.java),
        yaml = out.resolve("diff-report.yaml").readText(),
    )
}

private fun Any?.at(vararg path: Any): Any? = path.fold(this) { node, key ->
    when (key) {
        is String -> (node as? Map<*, *>)?.get(key)
        is Int -> (node as? List<*>)?.getOrNull(key)
        else -> null
    }
}

// The sections of the JSON report that list [path]: the qualified names of the roles, then the keys of the files no role owns.
private fun sectionsOf(json: Any?, path: String): List<String> {
    fun lists(files: Any?) = (files as? List<*>).orEmpty().any { it.at("path") == path }
    fun roles(nodes: Any?): List<String> = (nodes as? List<*>).orEmpty().flatMap { node ->
        if (node.at("type") == "group") roles(node.at("children")) else listOfNotNull(node.at("qualifiedName")?.toString()?.takeIf { lists(node.at("files")) })
    }
    return roles(json.at("nodes")) + listOf("noRole", "baselined", "noRoleDeleted", "ignored").filter { lists(json.at(it, "files")) }
}

// The architecture test with -Dkatachi.baseline.update=true: the baseline records every violation of the tree as it is now.
private fun Architecture.recordBaseline(repo: Repository) {
    assertWith(RealFileSystem(repo.dir), emptyList(), maxViolations = null, environment = baselineEnvironment(update = true))
}

// What the architecture test fails on for the tree as it is now, after the baseline held back what it records.
private fun Architecture.failuresOfArchitectureTest(repo: Repository): List<String> = try {
    assertWith(RealFileSystem(repo.dir), emptyList(), maxViolations = null, environment = baselineEnvironment(update = false))
    emptyList()
} catch (failure: KatachiArchitectureAssertionError) {
    failure.violations.filter { it.severity == Severity.Error }.map { "[${it.label}] ${it.path}" }
}

// A run of its own each time: the runs of one JVM would otherwise refuse a second run that finds something else.
private fun baselineEnvironment(update: Boolean): BaselineEnvironment = BaselineEnvironment(
    systemProperty = { if (update && it == "katachi.baseline.update") "true" else null },
    environmentVariable = { null },
    standardError = {},
    runs = BaselineRuns(),
)

private class Repository(val dir: File) {
    fun write(path: String, content: String) {
        dir.resolve(path).apply { parentFile.mkdirs() }.writeText(content)
    }

    fun move(from: String, to: String) {
        dir.resolve(to).parentFile.mkdirs()
        git("mv", from, to)
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

// The way a reader runs a command copied from the report: pasted into a shell.
private fun sh(dir: File, command: String): String {
    val process = ProcessBuilder("sh", "-c", command).directory(dir).redirectErrorStream(true).start()
    val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
    if (process.waitFor() != 0) throw IllegalStateException("$command failed: $output")
    return output
}

// The Importance squares of the Markdown report, joined by U+2060 WORD JOINER so GitHub does not wrap them.
private fun squares(square: String, count: Int): String = List(count) { square }.joinToString("\u2060")

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
