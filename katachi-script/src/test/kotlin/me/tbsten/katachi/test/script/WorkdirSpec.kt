package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

/**
 * Which working directory and project root the commands use when the place is not obvious: run from a
 * `git worktree` of the recorded project, or with two working directories below the current one (KATACHI-354).
 */
class WorkdirSpec : FreeSpec({
    "別の作業ツリー（git worktree）から" - {
        "test は記録したメインではなく、いまいる作業ツリーを検査し、そう警告する" {
            val repo = WorktreeFixture()

            val run = repo.runFromWorktree("agent", "test")

            run.status shouldBe 0
            repo.worktree.resolve("build/katachi/test/latest.log").readText() shouldContain "args: from worktree"
            run.err shouldContain "別の作業ツリー（git worktree）です"
            run.err shouldContain repo.worktree.toString()
        }

        "ログは作業用ディレクトリではなく、その作業ツリーの build/katachi/ に置く（並べて走らせても latest.log を取り合わない）" {
            val repo = WorktreeFixture()

            repo.runFromWorktree("agent", "test", "--save", "before").status shouldBe 0

            repo.worktree.resolve("build/katachi/test/latest.log").readText() shouldContain "args: from worktree"
            repo.worktree.resolve("build/katachi/test-before.log").exists() shouldBe true
            repo.workdir.resolve("tmp/test/latest.log").exists() shouldBe false
        }

        "git worktree add で作った本物の作業ツリーでも同じ" {
            val main = createTempDirectory("katachi-real-main-")
            git(main, "init", "-q")
            gradleProject(main, "main", withDefinition = true)
            git(main, "add", ".")
            git(main, "commit", "-q", "-m", "init")
            val worktree = main.resolveSibling(main.fileName.toString() + "-wt")
            git(main, "worktree", "add", "-q", worktree.toString())
            // git keeps no empty directory, so the definition directory has to be made again in the new work tree.
            worktree.resolve("architecture-test/src/test/kotlin").createDirectories()
            worktree.resolve("gradlew").writeText("#!/bin/sh\necho \"args: from worktree\"\necho \"BUILD SUCCESSFUL in 1s\"\nexit 0\n")
            val workdir = main.resolve(".local/install-katachi")
            workdir.resolve("tmp").createDirectories()
            workdir.resolve("check-list.html").writeText(checkListHtml("""{"meta": {"projectRoot": "$main"}, "steps": []}""", "ja"))

            val run = runCapturing(worktree, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to workdir.toString()))

            run.status shouldBe 0
            worktree.resolve("build/katachi/test/latest.log").readText() shouldContain "args: from worktree"
            run.err shouldContain "別の作業ツリー（git worktree）です"
        }

        "警告は 1 回だけ出す" {
            val repo = WorktreeFixture()

            val run = repo.runFromWorktree("agent", "test")

            run.err.split("別の作業ツリー").size shouldBe 2
        }

        "プロジェクトがリポジトリの下の階層なら、作業ツリーの同じ階層を検査する" {
            val repo = WorktreeFixture(projectDir = "apps/android")

            val run = repo.runFromWorktree("agent", "test")

            run.status shouldBe 0
            repo.worktree.resolve("apps/android/build/katachi/test/latest.log").readText() shouldContain "args: from worktree"
            run.err shouldContain repo.worktree.resolve("apps/android").toString()
        }

        "lint もいまいる作業ツリーの定義を読む" {
            val repo = WorktreeFixture(definitionInMain = false)

            val run = repo.runFromWorktree("lint")

            run.err shouldNotContain "定義のディレクトリがありません"
            run.err shouldContain "別の作業ツリー（git worktree）です"
        }

        "メインの作業ツリーの中から走らせれば、これまでどおり記録した場所を検査する" {
            val repo = WorktreeFixture()

            val run = runCapturing(repo.main, listOf("agent", "test"), repo.env())

            run.status shouldBe 0
            repo.workdir.resolve("tmp/test/latest.log").readText() shouldContain "args: from main"
            run.err shouldNotContain "別の作業ツリー"
        }

        "別のリポジトリから走らせたときは、記録した場所を検査する" {
            val repo = WorktreeFixture()
            val other = createTempDirectory("katachi-other-repo-")
            other.resolve(".git").createDirectories()

            val run = runCapturing(other, listOf("agent", "test"), repo.env())

            run.status shouldBe 0
            repo.workdir.resolve("tmp/test/latest.log").readText() shouldContain "args: from main"
            run.err shouldNotContain "別の作業ツリー"
        }
    }

    "作業用ディレクトリごと写したプロジェクトから（KATACHI-420）" - {
        "test は記録した元のプロジェクトではなく写しを検査し、そう警告して、検査したプロジェクトを出す" {
            val original = createTempDirectory("katachi-original-")
            projectRecording(original, "original", recordedRoot = original)
            val copy = createTempDirectory("katachi-copy-")
            val copyWorkdir = projectRecording(copy, "copy", recordedRoot = original)

            val run = runCapturing(copy, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            copyWorkdir.resolve("tmp/test/latest.log").readText() shouldContain "args: from copy"
            run.err shouldContain "プロジェクトを作業用ディレクトリごと写したか、移したようです。作業用ディレクトリのある file://$copy を検査します。"
            run.err.split("写したか、移したようです").size shouldBe 2
            run.out shouldContain "プロジェクト: file://$copy\n"
            original.resolve(".local/install-katachi/tmp/test").exists() shouldBe false
        }

        "meta.workdir があれば、写しの外（上）から実行しても写しのプロジェクトを検査する" {
            val original = createTempDirectory("katachi-original-")
            projectRecording(original, "original", recordedRoot = original, recordedWorkdir = ".local/install-katachi")
            val parent = createTempDirectory("katachi-copies-")
            val copy = parent.resolve("copy")
            val copyWorkdir = projectRecording(copy, "copy", recordedRoot = original, recordedWorkdir = ".local/install-katachi")

            val run = runCapturing(parent, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            copyWorkdir.resolve("tmp/test/latest.log").readText() shouldContain "args: from copy"
            run.out shouldContain "プロジェクト: file://$copy\n"
        }

        "meta.workdir があれば、元のプロジェクトの中に写しても写しを検査する" {
            val original = createTempDirectory("katachi-original-")
            projectRecording(original, "original", recordedRoot = original, recordedWorkdir = ".local/install-katachi")
            val copy = original.resolve("copies/copy")
            val copyWorkdir = projectRecording(copy, "copy", recordedRoot = original, recordedWorkdir = ".local/install-katachi")

            val run = runCapturing(copy, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            copyWorkdir.resolve("tmp/test/latest.log").readText() shouldContain "args: from copy"
        }

        "記録した元のプロジェクトがもう無くても（移した）、作業用ディレクトリのあるプロジェクトを検査する" {
            val moved = createTempDirectory("katachi-moved-")
            val workdir = projectRecording(moved, "moved", recordedRoot = moved.resolveSibling(moved.fileName.toString() + "-before-move"))

            val run = runCapturing(moved, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            workdir.resolve("tmp/test/latest.log").readText() shouldContain "args: from moved"
            run.err shouldContain "写したか、移したようです"
        }

        "lint も写しの定義を読む" {
            val original = createTempDirectory("katachi-original-")
            projectRecording(original, "original", recordedRoot = original, withDefinition = false)
            val copy = createTempDirectory("katachi-copy-")
            projectRecording(copy, "copy", recordedRoot = original)
            copy.resolve("architecture-test/src/test/kotlin/MixedRole.kt")
                .writeText("val m = \"Mixed\".role { layout { \"README.md\".file(); \"LICENSE\".file() } }\n")

            val run = runCapturing(copy, listOf("lint"))

            run.status shouldBe 0
            run.err shouldNotContain "定義のディレクトリがありません"
            run.out shouldContain "定義: file://${copy.resolve("architecture-test/src/test/kotlin")}"
            run.out shouldContain "種類の違うファイルを1つの Role に抱えていそうなものが 1 件あります（1 件を走査）。"
            run.err shouldContain "写したか、移したようです"
        }

        "compare-violations も写しの定義の場所で比べる" {
            val original = createTempDirectory("katachi-original-")
            projectRecording(original, "original", recordedRoot = original, withDefinition = false)
            val copy = createTempDirectory("katachi-copy-")
            val copyWorkdir = projectRecording(copy, "copy", recordedRoot = original)
            copyWorkdir.resolve("tmp/test-before-refactor.log").writeText("> Task :architecture-test:test\nBUILD SUCCESSFUL in 3s\n")
            copyWorkdir.resolve("tmp/test-after-refactor.log").writeText("> Task :architecture-test:test\nBUILD SUCCESSFUL in 3s\n")

            val run = runCapturing(copy, listOf("agent", "compare-violations"))

            run.status shouldBe 0
            run.out shouldContain "検査結果は変わっていません"
            run.err shouldContain "写したか、移したようです"
        }

        "元のプロジェクトの上から実行しても、作業用ディレクトリがそのプロジェクトの中なら記録したほうを検査し、警告しない" {
            val parent = createTempDirectory("katachi-parent-")
            val project = parent.resolve("app")
            val workdir = projectRecording(project, "app", recordedRoot = project)

            val run = runCapturing(parent, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            workdir.resolve("tmp/test/latest.log").readText() shouldContain "args: from app"
            run.err shouldNotContain "写したか"
            run.out shouldContain "プロジェクト: file://$project\n"
        }

        "KATACHI_WORKDIR で指した作業用ディレクトリは、プロジェクトの外にあっても記録したほうを検査する" {
            val project = createTempDirectory("katachi-project-")
            gradleProject(project, "project", withDefinition = true)
            val workdir = workdirWith("""{"meta": {"projectRoot": "$project"}, "steps": []}""")
            val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to workdir.toString())

            val run = runCapturing(createTempDirectory("katachi-elsewhere-"), listOf("agent", "test"), env)

            run.status shouldBe 0
            workdir.resolve("tmp/test/latest.log").readText() shouldContain "args: from project"
            run.err shouldNotContain "写したか"
        }
    }

    "KATACHI_WORKDIR が作業用ディレクトリを指していないと" - {
        "test は黙って作業用ディレクトリ無しで進まず、値を出して止まる" {
            val project = projectWithTwoWorkdirs()
            val missing = project.resolve("no-such-workdir")
            val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to missing.toString())

            val run = runCapturing(project, listOf("agent", "test"), env)

            run.status shouldBe 1
            run.err shouldContain "KATACHI_WORKDIR（${missing}）は作業用ディレクトリではありません"
            project.resolve("build/katachi").exists() shouldBe false
        }

        "doctor は止まらず、警告する" {
            val project = projectWithTwoWorkdirs()
            val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to project.resolve("no-such-workdir").toString())

            val run = runCapturing(project, listOf("doctor"), env)

            run.status shouldBe 0
            run.err shouldContain "は作業用ディレクトリではありません"
        }
    }

    "作業用ディレクトリが 2 つあると" - {
        "test は黙って既定値に戻らず、候補を並べて止まる" {
            val project = projectWithTwoWorkdirs()

            val run = runCapturing(project, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 1
            run.err shouldContain "作業用ディレクトリ（check-list.html と tmp/ のあるディレクトリ）が 2 つ以上あり"
            run.err shouldContain project.resolve(".local/install-katachi").toString()
            run.err shouldContain project.resolve(".local/install-katachi-old").toString()
            run.err shouldContain "KATACHI_WORKDIR"
        }

        "scaffold も止まる（moduleDir を既定値で作らない）" {
            val project = projectWithTwoWorkdirs()

            val run = runCapturing(project, listOf("scaffold", "--package", "com.example"))

            run.status shouldBe 1
            run.err shouldContain "KATACHI_WORKDIR"
            project.resolve("architecture-test").exists() shouldBe false
        }

        "KATACHI_WORKDIR で選べば、そちらを使う" {
            val project = projectWithTwoWorkdirs()
            val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to project.resolve(".local/install-katachi").toString())

            val run = runCapturing(project, listOf("agent", "test"), env)

            run.status shouldBe 0
            run.err shouldNotContain "2 つ以上"
            project.resolve(".local/install-katachi/tmp/test/latest.log").exists() shouldBe true
        }

        "どちらも同じ言語なら、止まるときのメッセージもその言語で出す" {
            val project = projectWithTwoWorkdirs(lang = "en")

            val run = runCapturing(project, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 1
            run.err shouldContain "There are two or more working directories"
        }

        "doctor は止まらず、2 つあることを警告する" {
            val project = projectWithTwoWorkdirs()

            val run = runCapturing(project, listOf("doctor"))

            run.status shouldBe 0
            run.err shouldContain "2 つ以上あり"
        }

        "--help は止まらない" {
            val project = projectWithTwoWorkdirs()

            runCapturing(project, listOf("--help")).status shouldBe 0
        }
    }

    // root opens every directory, so there the locked one would not be locked and the case would prove nothing.
    "下に開けないディレクトリがあっても、--version も doctor も止まらず、作業用ディレクトリも見つける"
        .config(enabledIf = { System.getProperty("user.name") != "root" }) {
            val project = projectWithWorkdir("""{"meta": {}, "steps": []}""")
            gradleProject(project, "project", withDefinition = true)
            val locked = project.resolve("someone-else").createDirectories()
            locked.setPosixFilePermissions(PosixFilePermissions.fromString("---------"))
            try {
                runCapturing(project, listOf("--version")).status shouldBe 0
                runCapturing(project, listOf("doctor")).err shouldNotContain "Exception"

                val test = runCapturing(project, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH")))

                test.status shouldBe 0
                project.resolve(".local/install-katachi/tmp/test/latest.log").exists() shouldBe true
            } finally {
                locked.setPosixFilePermissions(PosixFilePermissions.fromString("rwx------"))
            }
        }

    // root reads every file, so there the locked check list would not be locked and the case would prove nothing.
    "下に読めない check-list.html（tmp/ 付き）があっても、--version も --help も test も止まらず、読める作業用ディレクトリを使う（KATACHI-420）"
        .config(enabledIf = { System.getProperty("user.name") != "root" }) {
            val project = projectWithWorkdir("""{"meta": {}, "steps": []}""")
            gradleProject(project, "project", withDefinition = true)
            val other = project.resolve("someone-else/.local/install-katachi")
            other.resolve("tmp").createDirectories()
            val locked = other.resolve("check-list.html")
            locked.writeText(checkListHtml("""{"meta": {}, "steps": []}""", "en"))
            locked.setPosixFilePermissions(PosixFilePermissions.fromString("---------"))
            try {
                runCapturing(project, listOf("--version")).status shouldBe 0
                val help = runCapturing(project, listOf("--help"))
                help.status shouldBe 0
                help.err shouldNotContain "Exception"
                help.err shouldContain "読めない check-list.html は、作業用ディレクトリとして使いません"
                help.err shouldContain locked.toString()

                val test = runCapturing(project, listOf("agent", "test"), mapOf("PATH" to System.getenv("PATH")))

                test.status shouldBe 0
                test.err shouldNotContain "2 つ以上"
                test.err.split("読めない check-list.html").size shouldBe 2
                project.resolve(".local/install-katachi/tmp/test/latest.log").exists() shouldBe true
            } finally {
                locked.setPosixFilePermissions(PosixFilePermissions.fromString("rw-------"))
            }
        }

    "KATACHI_WORKDIR の check-list.html が読めないと、作業用ディレクトリではないと言って止まる（KATACHI-420）"
        .config(enabledIf = { System.getProperty("user.name") != "root" }) {
            val project = projectWithWorkdir("""{"meta": {}, "steps": []}""")
            gradleProject(project, "project", withDefinition = true)
            val workdir = project.resolve(".local/install-katachi")
            val locked = workdir.resolve("check-list.html")
            locked.setPosixFilePermissions(PosixFilePermissions.fromString("---------"))
            try {
                val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to workdir.toString())

                val run = runCapturing(project, listOf("agent", "test"), env)

                run.status shouldBe 1
                run.err shouldNotContain "Exception"
                run.err shouldContain "は作業用ディレクトリではありません"
            } finally {
                locked.setPosixFilePermissions(PosixFilePermissions.fromString("rw-------"))
            }
        }
})

/**
 * A repository whose main work tree holds the working directory (recording [projectDir] of it as the
 * project root), and a linked worktree of it laid out as `git worktree add` leaves it. The `gradlew` of
 * each prints which one ran.
 */
private class WorktreeFixture(projectDir: String = "", definitionInMain: Boolean = true) {
    val main: Path = createTempDirectory("katachi-main-")
    val worktree: Path = createTempDirectory("katachi-worktree-")
    private val mainProject = main.resolve(projectDir).normalize()
    val workdir: Path = main.resolve(".local/install-katachi")

    init {
        main.resolve(".git/worktrees/wt").createDirectories()
        main.resolve(".git/worktrees/wt/commondir").writeText("../..\n")
        worktree.resolve(".git").writeText("gitdir: ${main.resolve(".git/worktrees/wt")}\n")

        workdir.resolve("tmp").createDirectories()
        workdir.resolve("check-list.html").writeText(checkListHtml("""{"meta": {"projectRoot": "$mainProject"}, "steps": []}""", "ja"))

        gradleProject(mainProject, "main", withDefinition = definitionInMain)
        gradleProject(worktree.resolve(projectDir).normalize(), "worktree", withDefinition = true)
    }

    fun env(): Map<String, String> = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to workdir.toString())

    fun runFromWorktree(vararg args: String): RecordedRun = runCapturing(worktree, args.toList(), env())
}

private fun gradleProject(dir: Path, name: String, withDefinition: Boolean) {
    dir.createDirectories()
    if (withDefinition) dir.resolve("architecture-test/src/test/kotlin").createDirectories()
    dir.resolve("gradlew").writeText("#!/bin/sh\necho \"args: from $name\"\necho \"BUILD SUCCESSFUL in 1s\"\nexit 0\n")
    dir.resolve("gradlew").setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
}

/**
 * [dir] as a project whose `gradlew` prints [name], with the working directory in `.local/install-katachi` recording
 * [recordedRoot] as `meta.projectRoot` (and [recordedWorkdir] as `meta.workdir`), as `cp -r` leaves a copy. Returns the
 * working directory.
 */
private fun projectRecording(dir: Path, name: String, recordedRoot: Path, recordedWorkdir: String? = null, withDefinition: Boolean = true): Path {
    gradleProject(dir, name, withDefinition)
    val workdir = dir.resolve(".local/install-katachi")
    workdir.resolve("tmp").createDirectories()
    val meta = listOfNotNull("\"projectRoot\": \"$recordedRoot\"", recordedWorkdir?.let { "\"workdir\": \"$it\"" }).joinToString(", ")
    workdir.resolve("check-list.html").writeText(checkListHtml("""{"meta": {$meta}, "steps": []}""", "ja"))
    return workdir
}

private fun git(dir: Path, vararg args: String) {
    val command = listOf("git", "-c", "user.name=katachi", "-c", "user.email=katachi@example.com", "-c", "commit.gpgsign=false") + args
    val process = ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
}

/** A project holding a new working directory and an old one left from an earlier setup. */
private fun projectWithTwoWorkdirs(lang: String? = null): Path {
    val project = projectWithWorkdir("""{"meta": {"moduleDir": "static-analysis/katachi"}, "steps": []}""", lang ?: "ja")
    val old = project.resolve(".local/install-katachi-old")
    old.resolve("tmp").createDirectories()
    old.resolve("check-list.html").writeText(checkListHtml("""{"meta": {}, "steps": []}""", lang ?: "en"))
    gradleProject(project, "project", withDefinition = false)
    project.resolve("static-analysis/katachi/src/test/kotlin").createDirectories()
    return project
}
