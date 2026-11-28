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
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

/**
 * Which working directory and project root the commands use when the place is not obvious: run from a
 * `git worktree` of the recorded project, or with two working directories below the current one (KATACHI-354).
 */
class WorkdirSpec : FreeSpec({
    "別の作業ツリー（git worktree）から" - {
        "lint は記録したメインではなく、いまいる作業ツリーの定義を読み、そう警告する" {
            val repo = WorktreeFixture(definitionInMain = false)

            val run = repo.runFromWorktree("lint")

            run.status shouldBe 0
            run.err shouldContain "別の作業ツリー（git worktree）です"
            run.err shouldContain repo.worktree.toString()
        }

        "git worktree add で作った本物の作業ツリーでも同じ" {
            val main = createTempDirectory("katachi-real-main-")
            git(main, "init", "-q")
            definitionProject(main, withDefinition = true)
            // git keeps no empty directory, so commit something for the work tree to be made from.
            main.resolve("settings.gradle.kts").writeText("")
            git(main, "add", ".")
            git(main, "commit", "-q", "-m", "init")
            val worktree = main.resolveSibling(main.fileName.toString() + "-wt")
            git(main, "worktree", "add", "-q", worktree.toString())
            // git keeps no empty directory, so the definition directory has to be made again in the new work tree.
            worktree.resolve("architecture-test/src/test/kotlin").createDirectories()
            val workdir = main.resolve(".local/install-katachi")
            workdir.resolve("tmp").createDirectories()
            workdir.resolve("check-list.html").writeText(checkListHtml("""{"meta": {"projectRoot": "$main"}, "steps": []}""", "ja"))

            val run = runWithDefinition(worktree, listOf("lint"), mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to workdir.toString()))

            run.status shouldBe 0
            run.err shouldContain "別の作業ツリー（git worktree）です"
        }

        "警告は 1 回だけ出す" {
            val repo = WorktreeFixture()

            val run = repo.runFromWorktree("lint")

            run.err.split("別の作業ツリー").size shouldBe 2
        }

        "プロジェクトがリポジトリの下の階層なら、作業ツリーの同じ階層を検査する" {
            val repo = WorktreeFixture(projectDir = "apps/android")

            val run = repo.runFromWorktree("lint")

            run.status shouldBe 0
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

            val run = runWithDefinition(repo.main, listOf("lint"), repo.env())

            run.status shouldBe 0
            run.err shouldNotContain "別の作業ツリー"
        }

        "別のリポジトリから走らせたときは、記録した場所を検査する" {
            val repo = WorktreeFixture()
            val other = createTempDirectory("katachi-other-repo-")
            other.resolve(".git").createDirectories()

            val run = runWithDefinition(other, listOf("lint"), repo.env())

            run.status shouldBe 0
            run.err shouldNotContain "別の作業ツリー"
        }
    }

    "作業用ディレクトリごと写したプロジェクトから（KATACHI-420）" - {
        "lint は記録した元のプロジェクトではなく写しの定義を読み、そう警告する" {
            val original = createTempDirectory("katachi-original-")
            projectRecording(original, recordedRoot = original)
            val copy = createTempDirectory("katachi-copy-")
            projectRecording(copy, recordedRoot = original)

            val run = runWithDefinition(copy, listOf("lint"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            run.err shouldContain "プロジェクトを作業用ディレクトリごと写したか、移したようです。作業用ディレクトリのある file://$copy を検査します。"
            run.err.split("写したか、移したようです").size shouldBe 2
            run.out shouldContain "定義: file://${copy.resolve("architecture-test/src/test/kotlin")}"
        }

        "meta.workdir があれば、写しの外（上）から実行しても写しのプロジェクトを読む" {
            val original = createTempDirectory("katachi-original-")
            projectRecording(original, recordedRoot = original, recordedWorkdir = ".local/install-katachi")
            val parent = createTempDirectory("katachi-copies-")
            val copy = parent.resolve("copy")
            projectRecording(copy, recordedRoot = original, recordedWorkdir = ".local/install-katachi")

            val run = runWithDefinition(parent, listOf("lint"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            run.out shouldContain "定義: file://${copy.resolve("architecture-test/src/test/kotlin")}"
        }

        "meta.workdir があれば、元のプロジェクトの中に写しても写しを読む" {
            val original = createTempDirectory("katachi-original-")
            projectRecording(original, recordedRoot = original, recordedWorkdir = ".local/install-katachi")
            val copy = original.resolve("copies/copy")
            projectRecording(copy, recordedRoot = original, recordedWorkdir = ".local/install-katachi")

            val run = runWithDefinition(copy, listOf("lint"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            run.out shouldContain "定義: file://${copy.resolve("architecture-test/src/test/kotlin")}"
        }

        "記録した元のプロジェクトがもう無くても（移した）、作業用ディレクトリのあるプロジェクトを読む" {
            val moved = createTempDirectory("katachi-moved-")
            projectRecording(moved, recordedRoot = moved.resolveSibling(moved.fileName.toString() + "-before-move"))

            val run = runWithDefinition(moved, listOf("lint"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            run.out shouldContain "定義: file://${moved.resolve("architecture-test/src/test/kotlin")}"
            run.err shouldContain "写したか、移したようです"
        }

        "lint も写しの定義を読む" {
            val original = createTempDirectory("katachi-original-")
            projectRecording(original, recordedRoot = original, withDefinition = false)
            val copy = createTempDirectory("katachi-copy-")
            projectRecording(copy, recordedRoot = original)

            val run = runWithDefinition(copy, listOf("lint"))

            run.status shouldBe 0
            run.err shouldNotContain "定義のディレクトリがありません"
            run.out shouldContain "定義: file://${copy.resolve("architecture-test/src/test/kotlin")}"
            run.out shouldContain "種類の違うファイルを1つの Role に抱えていそうなものが 1 件あります（1 件を走査）。"
            run.err shouldContain "写したか、移したようです"
        }

        "元のプロジェクトの上から実行しても、作業用ディレクトリがそのプロジェクトの中なら記録したほうを読み、警告しない" {
            val parent = createTempDirectory("katachi-parent-")
            val project = parent.resolve("app")
            projectRecording(project, recordedRoot = project)

            val run = runWithDefinition(parent, listOf("lint"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 0
            run.err shouldNotContain "写したか"
            run.out shouldContain "定義: file://${project.resolve("architecture-test/src/test/kotlin")}"
        }

        "KATACHI_WORKDIR で指した作業用ディレクトリは、プロジェクトの外にあっても記録したほうを読む" {
            val project = createTempDirectory("katachi-project-")
            definitionProject(project, withDefinition = true)
            val workdir = workdirWith("""{"meta": {"projectRoot": "$project"}, "steps": []}""")
            val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to workdir.toString())

            val run = runWithDefinition(createTempDirectory("katachi-elsewhere-"), listOf("lint"), env)

            run.status shouldBe 0
            run.out shouldContain "定義: file://${project.resolve("architecture-test/src/test/kotlin")}"
            run.err shouldNotContain "写したか"
        }
    }

    "KATACHI_WORKDIR が作業用ディレクトリを指していないと" - {
        "lint は黙って作業用ディレクトリ無しで進まず、値を出して止まる" {
            val project = projectWithTwoWorkdirs()
            val missing = project.resolve("no-such-workdir")
            val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to missing.toString())

            val run = runWithDefinition(project, listOf("lint"), env)

            run.status shouldBe 1
            run.err shouldContain "KATACHI_WORKDIR（${missing}）は作業用ディレクトリではありません"
        }

        "doctor は止まらず、警告する" {
            val project = projectWithTwoWorkdirs()
            val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to project.resolve("no-such-workdir").toString())

            val run = runWithDefinition(project, listOf("doctor"), env)

            run.status shouldBe 0
            run.err shouldContain "は作業用ディレクトリではありません"
        }
    }

    "作業用ディレクトリが 2 つあると" - {
        "lint は黙って既定値に戻らず、候補を並べて止まる" {
            val project = projectWithTwoWorkdirs()

            val run = runWithDefinition(project, listOf("lint"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 1
            run.err shouldContain "作業用ディレクトリ（check-list.html と tmp/ のあるディレクトリ）が 2 つ以上あり"
            run.err shouldContain project.resolve(".local/install-katachi").toString()
            run.err shouldContain project.resolve(".local/install-katachi-old").toString()
            run.err shouldContain "KATACHI_WORKDIR"
        }

        "scaffold も止まる（moduleDir を既定値で作らない）" {
            val project = projectWithTwoWorkdirs()

            val run = runWithDefinition(project, listOf("scaffold", "--package", "com.example"))

            run.status shouldBe 1
            run.err shouldContain "KATACHI_WORKDIR"
            project.resolve("architecture-test").exists() shouldBe false
        }

        "KATACHI_WORKDIR で選べば、そちらを使う" {
            val project = projectWithTwoWorkdirs()
            val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to project.resolve(".local/install-katachi").toString())

            val run = runCapturing(project, listOf("lint"), env, gradle = FakeGradle(moduleDir = "static-analysis/katachi"))

            run.status shouldBe 0
            run.err shouldNotContain "2 つ以上"
        }

        "どちらも同じ言語なら、止まるときのメッセージもその言語で出す" {
            val project = projectWithTwoWorkdirs(lang = "en")

            val run = runWithDefinition(project, listOf("lint"), mapOf("PATH" to System.getenv("PATH")))

            run.status shouldBe 1
            run.err shouldContain "There are two or more working directories"
        }

        "doctor は止まらず、2 つあることを警告する" {
            val project = projectWithTwoWorkdirs()

            val run = runWithDefinition(project, listOf("doctor"))

            run.status shouldBe 0
            run.err shouldContain "2 つ以上あり"
        }

        "--help は止まらない" {
            val project = projectWithTwoWorkdirs()

            runWithDefinition(project, listOf("--help")).status shouldBe 0
        }
    }

    // root opens every directory, so there the locked one would not be locked and the case would prove nothing.
    "下に開けないディレクトリがあっても、--version も doctor も止まらず、作業用ディレクトリも見つける"
        .config(enabledIf = { System.getProperty("user.name") != "root" }) {
            val project = projectWithWorkdir("""{"meta": {}, "steps": []}""")
            definitionProject(project, withDefinition = true)
            val locked = project.resolve("someone-else").createDirectories()
            locked.setPosixFilePermissions(PosixFilePermissions.fromString("---------"))
            try {
                runWithDefinition(project, listOf("--version")).status shouldBe 0
                runWithDefinition(project, listOf("doctor")).err shouldNotContain "Exception"

                val lint = runWithDefinition(project, listOf("lint"), mapOf("PATH" to System.getenv("PATH")))

                lint.status shouldBe 0
                lint.err shouldNotContain "定義のディレクトリがありません"
            } finally {
                locked.setPosixFilePermissions(PosixFilePermissions.fromString("rwx------"))
            }
        }

    // root reads every file, so there the locked check list would not be locked and the case would prove nothing.
    "下に読めない check-list.html（tmp/ 付き）があっても、--version も --help も lint も止まらず、読める作業用ディレクトリを使う（KATACHI-420）"
        .config(enabledIf = { System.getProperty("user.name") != "root" }) {
            val project = projectWithWorkdir("""{"meta": {}, "steps": []}""")
            definitionProject(project, withDefinition = true)
            val other = project.resolve("someone-else/.local/install-katachi")
            other.resolve("tmp").createDirectories()
            val locked = other.resolve("check-list.html")
            locked.writeText(checkListHtml("""{"meta": {}, "steps": []}""", "en"))
            locked.setPosixFilePermissions(PosixFilePermissions.fromString("---------"))
            try {
                runWithDefinition(project, listOf("--version")).status shouldBe 0
                val help = runWithDefinition(project, listOf("--help"))
                help.status shouldBe 0
                help.err shouldNotContain "Exception"
                help.err shouldContain "読めない check-list.html は、作業用ディレクトリとして使いません"
                help.err shouldContain locked.toString()

                val lint = runWithDefinition(project, listOf("lint"), mapOf("PATH" to System.getenv("PATH")))

                lint.status shouldBe 0
                lint.err shouldNotContain "2 つ以上"
                lint.err.split("読めない check-list.html").size shouldBe 2
                lint.out shouldContain "定義: file://${project.resolve("architecture-test/src/test/kotlin")}"
            } finally {
                locked.setPosixFilePermissions(PosixFilePermissions.fromString("rw-------"))
            }
        }

    "KATACHI_WORKDIR の check-list.html が読めないと、作業用ディレクトリではないと言って止まる（KATACHI-420）"
        .config(enabledIf = { System.getProperty("user.name") != "root" }) {
            val project = projectWithWorkdir("""{"meta": {}, "steps": []}""")
            definitionProject(project, withDefinition = true)
            val workdir = project.resolve(".local/install-katachi")
            val locked = workdir.resolve("check-list.html")
            locked.setPosixFilePermissions(PosixFilePermissions.fromString("---------"))
            try {
                val env = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to workdir.toString())

                val run = runWithDefinition(project, listOf("lint"), env)

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
 * project root), and a linked worktree of it laid out as `git worktree add` leaves it.
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

        definitionProject(mainProject, withDefinition = definitionInMain)
        definitionProject(worktree.resolve(projectDir).normalize(), withDefinition = true)
    }

    fun env(): Map<String, String> = mapOf("PATH" to System.getenv("PATH"), "KATACHI_WORKDIR" to workdir.toString())

    fun runFromWorktree(vararg args: String): RecordedRun = runWithDefinition(worktree, args.toList(), env())
}

/** [runCapturing] with a Gradle that answers with the one Role [definitionProject] writes, into the project root it is asked to run in. */
private fun runWithDefinition(cwd: Path, args: List<String>, env: Map<String, String> = emptyMap()): RecordedRun = runCapturing(
    cwd,
    args,
    env,
    gradle = FakeGradle(json = definitionJson(roles = listOf(roleJson("Mixed", "MixedRole.kt", layout = listOf(layoutFile("README.md"), layoutFile("LICENSE")))))),
)

private fun definitionProject(dir: Path, withDefinition: Boolean) {
    dir.createDirectories()
    if (withDefinition) {
        dir.resolve("gradlew").writeText("")
        val definition = dir.resolve("architecture-test/src/test/kotlin").createDirectories()
        // lint names the definition directory it read only when it reports something, so give it one finding.
        definition.resolve("MixedRole.kt").writeText("val m = \"Mixed\".role { layout { \"README.md\".file(); \"LICENSE\".file() } }\n")
    }
}

/**
 * [dir] as a project with a definition, with the working directory in `.local/install-katachi` recording
 * [recordedRoot] as `meta.projectRoot` (and [recordedWorkdir] as `meta.workdir`), as `cp -r` leaves a copy. Returns the
 * working directory.
 */
private fun projectRecording(dir: Path, recordedRoot: Path, recordedWorkdir: String? = null, withDefinition: Boolean = true): Path {
    definitionProject(dir, withDefinition)
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
    definitionProject(project, withDefinition = false)
    project.resolve("static-analysis/katachi/src/test/kotlin").createDirectories()
    project.resolve("gradlew").writeText("")
    return project
}
