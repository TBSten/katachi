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
