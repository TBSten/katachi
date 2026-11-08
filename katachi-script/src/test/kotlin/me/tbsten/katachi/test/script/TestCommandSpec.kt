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
 * `test` and `compare-violations`. `test` starts the project's real `./gradlew`; here that is a
 * shell script printing a prepared log, so the summary is checked without Gradle (KATACHI-266).
 */
class TestCommandSpec : FreeSpec({
    "test" - {
        "違反があれば 1 で、結果の行と [MissingDescription] の数を出し、ログを残す" {
            val fixture = TestFixture()
            fixture.gradleOutput(FAILED_LOG, status = 1)

            val run = fixture.run("agent", "test", "--save", "before")

            run.status shouldBe 1
            run.out shouldContain "結果: Katachi check failed: 2 violations"
            run.out shouldContain "[MissingDescription]: 1 件（説明を書けば減る）"
            val latest = fixture.workdir.resolve("tmp/test/latest.log")
            latest.readText() shouldContain "[UnexpectedFile] app/src/Foo.kt"
            fixture.workdir.resolve("tmp/test-before.log").readText() shouldBe latest.readText()
        }

        "検査用モジュールの test を、全件を出す -D つきで呼ぶ（-- の後ろはそのまま渡す）" {
            val fixture = TestFixture()
            fixture.gradleOutput(PASSED_LOG, status = 0)

            fixture.run("agent", "test", "--", "--info").status shouldBe 0

            fixture.workdir.resolve("tmp/test/latest.log").readText() shouldContain
                "args: :architecture-test:test --console=plain --info -Dkatachi.maxViolations=all"
        }

        "通れば 0 で、前回との違反の差分を出す" {
            val fixture = TestFixture()
            fixture.gradleOutput(FAILED_LOG, status = 1)
            fixture.run("agent", "test").status shouldBe 1
            fixture.gradleOutput(PASSED_LOG, status = 0)

            val run = fixture.run("agent", "test")

            run.status shouldBe 0
            run.out shouldContain "結果: 検査は通りました（違反 0 件）"
            run.out shouldContain "前回との差分（previous.log 比）: 増えた 0 件 / 減った 2 件"
            run.out shouldContain "  - [MissingFile] app/src/Bar.kt"
            run.out shouldContain "  - [UnexpectedFile] app/src/Foo.kt"
        }

        "同じ結果が続けば差分は無しと出す" {
            val fixture = TestFixture()
            fixture.gradleOutput(FAILED_LOG, status = 1)
            fixture.run("agent", "test")

            fixture.run("agent", "test").out shouldContain "前回との差分（previous.log 比）: 無し"
        }

        "通っても警告があれば数と見出しを出す" {
            val fixture = TestFixture()
            fixture.gradleOutput(
                """
                Katachi check found 1 warning.
                  [AmbiguousLayout] app/src/Both.kt
                BUILD SUCCESSFUL in 3s
                """.trimIndent(),
                status = 0,
            )

            val run = fixture.run("agent", "test")

            run.status shouldBe 0
            run.out shouldContain "警告: 1 件（検査は失敗させない。内容を見て、直すか残すかを決める）"
            run.out shouldContain "  [AmbiguousLayout] app/src/Both.kt"
        }

        "検査まで届かなければ 2 で、ログの末尾を出す" {
            val fixture = TestFixture()
            fixture.gradleOutput("e: file:///p/Arch.kt:3:5 Unresolved reference 'roel'.\nBUILD FAILED in 2s", status = 1)

            val run = fixture.run("agent", "test")

            run.status shouldBe 2
            run.out shouldContain "結果: 検査まで届いていません（コンパイルエラーなど。Gradle の終了コード 1）。ログの末尾:"
            run.out shouldContain "Unresolved reference 'roel'."
        }

        "検査は通ったがほかのテストが落ちていれば 1 で、FAILED の行を出す" {
            val fixture = TestFixture()
            fixture.gradleOutput("ExtraSpec > works FAILED\nThere were failing tests. See the report.", status = 1)

            val run = fixture.run("agent", "test")

            run.status shouldBe 1
            run.out shouldContain "検査の違反は出ていませんが、検査用モジュールのほかのテストが失敗しています"
            run.out shouldContain "ExtraSpec > works FAILED"
        }

        "打ち切られたログなら、省略されていることを出す" {
            val fixture = TestFixture()
            fixture.gradleOutput("Katachi check failed: 12 violations\n  [UnexpectedFile] a.kt\nShowing first 1 (11 more...)", status = 1)

            fixture.run("agent", "test").out shouldContain "省略: Showing first 1 (11 more...)。"
        }

        "gradlew が無ければ Gradle を呼ばずに 2 で止まる" {
            val fixture = TestFixture()
            fixture.project.resolve("gradlew").toFile().delete()

            val run = fixture.run("agent", "test")

            run.status shouldBe 2
            run.err shouldContain "gradlew がありません"
            fixture.workdir.resolve("tmp/test").exists() shouldBe false
        }

        "定義のディレクトリが無ければ 2 で止まる" {
            val fixture = TestFixture(withDefinition = false)
            fixture.gradleOutput(PASSED_LOG, status = 0)

            val run = fixture.run("agent", "test")

            run.status shouldBe 2
            run.err shouldContain "定義のディレクトリがありません"
        }

        "Gradle 向けの引数を -- の前に書くと止まる" {
            val run = TestFixture().run("agent", "test", "--info")
            run.status shouldBe 1
            run.err shouldContain "test: 知らないオプションです: --info（Gradle に渡す引数は -- の後ろに）"
        }
    }

    "compare-violations" - {
        "前後の検査結果が同じなら 0" {
            val fixture = TestFixture()
            fixture.refactorLogs(before = FAILED_LOG, after = FAILED_LOG.replace("in 4s", "in 9s"))

            val run = fixture.run("agent", "compare-violations")

            run.status shouldBe 0
            run.out shouldContain "違反の見出し: 前 2 件 / 後 2 件"
            run.out shouldContain "[MissingDescription]（比べない）: 前 1 件 / 後 1 件"
            run.out shouldContain "検査結果は変わっていません。"
        }

        "変わっていれば 1 で、前だけ・後だけの行を出す" {
            val fixture = TestFixture()
            fixture.refactorLogs(
                before = FAILED_LOG,
                after = "Katachi check failed: 2 violations\n  [UnexpectedFile] app/src/Foo.kt\n  [UnexpectedFile] architecture-test/src/test/kotlin/ui/UiRole.kt",
            )

            val run = fixture.run("agent", "compare-violations")

            run.status shouldBe 1
            run.out shouldContain "検査結果が変わっています"
            run.out shouldContain "  - [MissingFile] app/src/Bar.kt"
            run.out shouldContain "  + [UnexpectedFile] architecture-test/src/test/kotlin/ui/UiRole.kt"
            run.out shouldNotContain "  - [UnexpectedFile] app/src/Foo.kt"
        }

        "打ち切られたログがあれば比べずに 2" {
            val fixture = TestFixture()
            fixture.refactorLogs(before = FAILED_LOG, after = "Katachi check failed: 12 violations\nShowing first 10 (2 more...)")

            val run = fixture.run("agent", "compare-violations")

            run.status shouldBe 2
            run.err shouldContain "違反を先頭の数件だけ出したログがあるので、比べません。"
        }

        "検査まで届いていないログとは比べない" {
            val fixture = TestFixture()
            fixture.refactorLogs(before = FAILED_LOG, after = "e: Unresolved reference\nBUILD FAILED")

            val run = fixture.run("agent", "compare-violations")

            run.status shouldBe 1
            run.err shouldContain "に検査の結果がありません。"
        }

        "ログが無ければ保存先を案内して止まる" {
            val run = TestFixture().run("agent", "compare-violations")
            run.status shouldBe 1
            run.err shouldContain "test-before-refactor.log がありません。./gradlew :architecture-test:test の出力をこのパスに保存してください"
        }

        "比べるログを引数で指定できる" {
            val fixture = TestFixture()
            val a = fixture.project.resolve("a.log").also { it.writeText(FAILED_LOG) }
            val b = fixture.project.resolve("b.log").also { it.writeText(FAILED_LOG) }

            fixture.run("agent", "compare-violations", a.toString(), b.toString()).status shouldBe 0
        }
    }
})

private val FAILED_LOG = """
    > Task :architecture-test:test FAILED
    me.tbsten.katachi.check.KatachiArchitectureAssertionError: Katachi check failed: 2 violations, 1 warning
      [UnexpectedFile] app/src/Foo.kt
      [MissingDescription] Role "Repository"
      [MissingFile] app/src/Bar.kt
    BUILD FAILED in 4s
""".trimIndent()

private val PASSED_LOG = "> Task :architecture-test:test\nBUILD SUCCESSFUL in 3s"

/** A project with a working directory, a definition directory and a `gradlew` that prints [gradleOutput]. */
private class TestFixture(withDefinition: Boolean = true) {
    val project: Path = createTempDirectory("katachi-project-")
    val workdir: Path = workdirWith("""{"meta": {"projectRoot": "$project"}, "steps": []}""")

    init {
        if (withDefinition) project.resolve("architecture-test/src/test/kotlin").createDirectories()
        // Prints the arguments, then the prepared log, and exits with the prepared status.
        project.resolve("gradlew").writeText("#!/bin/sh\necho \"args: $*\"\ncat gradle-output.txt\nexit \"$(cat gradle-status.txt)\"\n")
        project.resolve("gradlew").setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
        gradleOutput(PASSED_LOG, status = 0)
    }

    fun gradleOutput(log: String, status: Int) {
        project.resolve("gradle-output.txt").writeText(log + "\n")
        project.resolve("gradle-status.txt").writeText("$status\n")
    }

    fun refactorLogs(before: String, after: String) {
        workdir.resolve("tmp/test-before-refactor.log").writeText(before + "\n")
        workdir.resolve("tmp/test-after-refactor.log").writeText(after + "\n")
    }

    // test hands Gradle only this environment, so the script needs PATH for cat.
    fun run(vararg args: String): RecordedRun =
        runCapturing(project, args.toList(), env = mapOf("KATACHI_WORKDIR" to workdir.toString(), "PATH" to System.getenv("PATH")))
}
