package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.setLastModifiedTime
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
            run.out shouldContain "[MissingDescription]: 1 件（置き場所に description を書けば減る）"
            val latest = fixture.workdir.resolve("tmp/test/latest.log")
            latest.readText() shouldContain "[UnexpectedFile] app/src/Foo.kt"
            fixture.workdir.resolve("tmp/test-before.log").readText() shouldBe latest.readText()
        }

        "作業用ディレクトリが無ければ、プロジェクトの build/katachi/ にログを残す（KATACHI-329）" {
            val fixture = TestFixture(withWorkdir = false)
            fixture.gradleOutput(FAILED_LOG, status = 1)

            val run = fixture.run("agent", "test", "--save", "before")

            run.status shouldBe 1
            fixture.project.resolve("build/katachi/test/latest.log").readText() shouldContain "[UnexpectedFile] app/src/Foo.kt"
            fixture.project.resolve("build/katachi/test-before.log").exists() shouldBe true
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
            run.status shouldBe 2
            run.err shouldContain "test: 知らないオプションです: --info（Gradle に渡す引数は -- の後ろに）"
        }

        "--module-dir（KATACHI-420）" - {
            "チェックリストが無くても、--module-dir の検査用モジュールの test を呼ぶ" {
                val fixture = TestFixture(withDefinition = false, withWorkdir = false)
                fixture.project.resolve("static-analysis/katachi/src/test/kotlin").createDirectories()

                val run = fixture.run("agent", "test", "--module-dir", "static-analysis/katachi")

                run.status shouldBe 0
                fixture.project.resolve("build/katachi/test/latest.log").readText() shouldContain "args: :static-analysis:katachi:test --console=plain"
            }

            "チェックリストの meta.moduleDir より --module-dir を使う" {
                val fixture = TestFixture(withDefinition = false)
                fixture.workdir.resolve("check-list.html")
                    .writeText(checkListHtml("""{"meta": {"projectRoot": "${fixture.project}", "moduleDir": "static-analysis/old"}, "steps": []}""", "ja"))
                fixture.project.resolve("static-analysis/katachi/src/test/kotlin").createDirectories()

                fixture.run("agent", "test", "--module-dir", "static-analysis/katachi").status shouldBe 0

                fixture.workdir.resolve("tmp/test/latest.log").readText() shouldContain "args: :static-analysis:katachi:test --console=plain"
            }

            "-- の後ろの --module-dir は Gradle に渡す" {
                val fixture = TestFixture()

                fixture.run("agent", "test", "--", "--module-dir", "x").status shouldBe 0

                fixture.workdir.resolve("tmp/test/latest.log").readText() shouldContain "args: :architecture-test:test --console=plain --module-dir x"
            }

            "定義のディレクトリが無ければ、--module-dir でも指定できると言う" {
                val run = TestFixture(withDefinition = false, withWorkdir = false).run("agent", "test")

                run.status shouldBe 2
                run.err shouldContain "違うなら --module-dir <パス> で指定するか、チェックリストの meta.moduleDir を直す"
            }

            "--module-dir で選んだモジュールなら、案内するコマンドにも --module-dir を付ける" {
                val fixture = TestFixture(withDefinition = false, withWorkdir = false)
                fixture.project.resolve("static-analysis/katachi/src/test/kotlin").createDirectories()
                fixture.gradleOutput("> Task :static-analysis:katachi:test UP-TO-DATE\nBUILD SUCCESSFUL in 1s", status = 0)

                val run = fixture.run("agent", "test", "--module-dir", "static-analysis/katachi")

                run.status shouldBe 2
                run.out shouldContain "katachi agent test --module-dir static-analysis/katachi -- --rerun"
            }

            "--module-dir が Gradle ルートからの相対パスでなければ、検査の失敗（1）ではなく使い方の誤り（2）で止まる" {
                val run = TestFixture().run("agent", "test", "--module-dir", "../outside")

                run.status shouldBe 2
                run.err shouldContain "--module-dir は Gradle ルートからの相対パスです"
            }
        }

        "テストが実行されていなければ（KATACHI-420）" - {
            "-- の後ろの Gradle の --dry-run でどのタスクも SKIPPED なら、通ったとは言わず 2" {
                val fixture = TestFixture()
                fixture.gradleOutput(DRY_RUN_LOG, status = 0)

                val run = fixture.run("agent", "test", "--", "--dry-run")

                run.status shouldBe 2
                run.out shouldContain "結果: テストが実行されていません（:architecture-test:test が SKIPPED）。何も検査していません。"
                run.out shouldContain "Gradle の --dry-run（-m）はタスクを実行しません。"
                run.out shouldNotContain "検査は通りました"
            }

            "@Disabled でテストがすべて SKIPPED なら 2（testLogging の SKIPPED の行から）" {
                val fixture = TestFixture()
                fixture.gradleOutput(
                    """
                    > Task :architecture-test:test

                    ProjectArchitectureTest > プロジェクトの構成が定義どおりになっている() SKIPPED

                    BUILD SUCCESSFUL in 3s
                    """.trimIndent(),
                    status = 0,
                )

                val run = fixture.run("agent", "test")

                run.status shouldBe 2
                run.out shouldContain "結果: テストが実行されていません（テスト 1 件がすべて SKIPPED）。"
                run.out shouldContain "@Disabled"
            }

            "testLogging がテストの行を出さない形（手動インストール）でも、JUnit XML ですべて SKIPPED なら 2 で、ログにも数を残す" {
                val fixture = TestFixture()
                fixture.gradleOutput(PASSED_LOG, status = 0)
                fixture.junitXmlWritten(tests = 1, skipped = 1)

                val run = fixture.run("agent", "test", "--save", "before-refactor")

                run.status shouldBe 2
                run.out shouldContain "結果: テストが実行されていません（テスト 1 件がすべて SKIPPED）。"
                fixture.workdir.resolve("tmp/test-before-refactor.log").readText() shouldContain
                    "katachi agent test: test cases in the JUnit XML of this run: 0 ran, 1 skipped"
            }

            "手動インストールの形で PASSED の行が無くても、JUnit XML でテストが実行されていれば 0" {
                val fixture = TestFixture()
                fixture.gradleOutput(PASSED_LOG, status = 0)
                fixture.junitXmlWritten(tests = 1, skipped = 0)

                val run = fixture.run("agent", "test")

                run.status shouldBe 0
                run.out shouldContain "結果: 検査は通りました（違反 0 件）"
                run.out shouldNotContain "SKIPPED のテスト"
            }

            "前の実行の JUnit XML は、今回書き直されていなければ数えない" {
                val fixture = TestFixture()
                fixture.gradleOutput(PASSED_LOG, status = 0)
                fixture.junitXmlLeftFromEarlierRun(tests = 1, skipped = 1)

                val run = fixture.run("agent", "test")

                run.status shouldBe 0
                run.out shouldContain "結果: 検査は通りました（違反 0 件）"
            }

            "テストのタスクが UP-TO-DATE なら前の結果なので 2 で、--rerun を案内する" {
                val fixture = TestFixture()
                fixture.gradleOutput("> Task :architecture-test:test UP-TO-DATE\nBUILD SUCCESSFUL in 1s", status = 0)

                val run = fixture.run("agent", "test")

                run.status shouldBe 2
                run.out shouldContain "（:architecture-test:test が UP-TO-DATE）"
                run.out shouldContain "katachi agent test -- --rerun"
            }

            "一部だけ SKIPPED なら通ったと言い、SKIPPED の数も出す" {
                val fixture = TestFixture()
                fixture.gradleOutput(PASSED_LOG, status = 0)
                fixture.junitXmlWritten(tests = 2, skipped = 1)

                val run = fixture.run("agent", "test")

                run.status shouldBe 0
                run.out shouldContain "SKIPPED のテスト: 1 件"
            }

            "前回が --dry-run のログなら、前回との差分を出さない" {
                val fixture = TestFixture()
                fixture.gradleOutput(DRY_RUN_LOG, status = 0)
                fixture.run("agent", "test", "--", "--dry-run").status shouldBe 2
                fixture.gradleOutput(FAILED_LOG, status = 1)

                val run = fixture.run("agent", "test")

                run.status shouldBe 1
                run.out shouldNotContain "前回との差分"
            }
        }
    }

    "compare-violations" - {
        "前後の検査結果が同じなら 0" {
            val fixture = TestFixture()
            fixture.refactorLogs(before = FAILED_LOG, after = FAILED_LOG.replace("in 4s", "in 9s"))

            val run = fixture.run("agent", "compare-violations")

            run.status shouldBe 0
            run.out shouldContain "見出し（違反と警告）: 前 2 件 / 後 2 件"
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

            run.status shouldBe 2
            run.err shouldContain "に検査の結果がありません。"
        }

        "テストが実行されていないログ（Gradle の --dry-run）は、前後とも同じでも比べずに 2（KATACHI-420）" {
            val fixture = TestFixture()
            fixture.refactorLogs(before = DRY_RUN_LOG, after = DRY_RUN_LOG)

            val run = fixture.run("agent", "compare-violations")

            run.status shouldBe 2
            run.err shouldContain "に検査の結果がありません。テストが実行されていません（:architecture-test:test が SKIPPED）。"
            run.out shouldNotContain "検査結果は変わっていません"
        }

        "--module-dir の定義の場所で比べ、ログが無ければ --module-dir 付きの取り方を案内する（KATACHI-420）" {
            val fixture = TestFixture(withDefinition = false, withWorkdir = false)
            fixture.project.resolve("static-analysis/katachi/src/test/kotlin").createDirectories()

            val missing = fixture.run("agent", "compare-violations", "--module-dir", "static-analysis/katachi")

            missing.status shouldBe 2
            missing.err shouldContain
                "katachi agent test --save <名前> --module-dir static-analysis/katachi で取るか、./gradlew :static-analysis:katachi:test の出力を"

            val logs = fixture.project.resolve("logs").createDirectories()
            logs.resolve("before.log").writeText("> Task :static-analysis:katachi:test\nBUILD SUCCESSFUL in 3s\n")
            logs.resolve("after.log").writeText("> Task :static-analysis:katachi:test\nBUILD SUCCESSFUL in 3s\n")

            val run = fixture.run("agent", "compare-violations", "logs/before.log", "--module-dir", "static-analysis/katachi", "logs/after.log")

            run.status shouldBe 0
            run.out shouldContain "検査結果は変わっていません"
        }

        "agent test が JUnit XML から実行 0 件と書き残したログとは比べない（KATACHI-420）" {
            val fixture = TestFixture()
            fixture.refactorLogs(
                before = FAILED_LOG,
                after = "$PASSED_LOG\nkatachi agent test: test cases in the JUnit XML of this run: 0 ran, 1 skipped",
            )

            val run = fixture.run("agent", "compare-violations")

            run.status shouldBe 2
            run.err shouldContain "テストが実行されていません（テスト 1 件がすべて SKIPPED）"
        }

        "ログが無ければ保存先を案内して止まる" {
            val run = TestFixture().run("agent", "compare-violations")
            run.status shouldBe 2
            run.err shouldContain "test-before-refactor.log がありません。katachi agent test --save <名前> で取るか、./gradlew :architecture-test:test の出力をこのパスに保存してください"
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

/** What `./gradlew :architecture-test:test --dry-run` prints: every task SKIPPED, and the build passes. */
private val DRY_RUN_LOG = """
    :architecture-test:compileTestKotlin SKIPPED
    :architecture-test:testClasses SKIPPED
    :architecture-test:test SKIPPED

    BUILD SUCCESSFUL in 8s
""".trimIndent()

private const val RESULTS_DIR = "architecture-test/build/test-results/test"

/** A JUnit XML result of one test class as Gradle writes it, with [tests] test cases of which [skipped] were skipped. */
private fun junitXml(tests: Int, skipped: Int): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <testsuite name="p.ProjectArchitectureTest" tests="$tests" skipped="$skipped" failures="0" errors="0" timestamp="2026-11-20T13:07:45.585Z" hostname="vm" time="0.37">
      <properties/>
      <testcase name="passes()" classname="p.ProjectArchitectureTest" time="0.21"/>
    </testsuite>
""".trimIndent()

/**
 * A project with a working directory (unless not [withWorkdir]), a definition directory and a `gradlew` that prints
 * [gradleOutput] and writes the JUnit XML of [junitXmlWritten], as the test task does.
 */
private class TestFixture(withDefinition: Boolean = true, private val withWorkdir: Boolean = true) {
    val project: Path = createTempDirectory("katachi-project-")
    val workdir: Path = workdirWith("""{"meta": {"projectRoot": "$project"}, "steps": []}""")

    init {
        if (withDefinition) project.resolve("architecture-test/src/test/kotlin").createDirectories()
        // Prints the arguments, then the prepared log, writes the prepared result, and exits with the prepared status.
        project.resolve("gradlew").writeText(
            """
            #!/bin/sh
            echo "args: $*"
            cat gradle-output.txt
            if [ -f gradle-junit.xml ]; then
              mkdir -p $RESULTS_DIR
              cp gradle-junit.xml $RESULTS_DIR/TEST-p.ProjectArchitectureTest.xml
            fi
            exit "$(cat gradle-status.txt)"
            """.trimIndent() + "\n",
        )
        project.resolve("gradlew").setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
        gradleOutput(PASSED_LOG, status = 0)
    }

    fun gradleOutput(log: String, status: Int) {
        project.resolve("gradle-output.txt").writeText(log + "\n")
        project.resolve("gradle-status.txt").writeText("$status\n")
    }

    /** The next run of `gradlew` writes a JUnit XML result. */
    fun junitXmlWritten(tests: Int, skipped: Int) {
        project.resolve("gradle-junit.xml").writeText(junitXml(tests, skipped) + "\n")
    }

    /** A JUnit XML result an earlier run left, an hour old, which the next run of `gradlew` does not touch. */
    fun junitXmlLeftFromEarlierRun(tests: Int, skipped: Int) {
        val file = project.resolve(RESULTS_DIR).createDirectories().resolve("TEST-p.ProjectArchitectureTest.xml")
        file.writeText(junitXml(tests, skipped) + "\n")
        file.setLastModifiedTime(FileTime.from(Instant.now().minus(Duration.ofHours(1))))
    }

    fun refactorLogs(before: String, after: String) {
        workdir.resolve("tmp/test-before-refactor.log").writeText(before + "\n")
        workdir.resolve("tmp/test-after-refactor.log").writeText(after + "\n")
    }

    // test hands Gradle only this environment, so the script needs PATH for cat.
    fun run(vararg args: String): RecordedRun =
        runCapturing(
            project,
            args.toList(),
            env = mapOf("PATH" to System.getenv("PATH")) + if (withWorkdir) mapOf("KATACHI_WORKDIR" to workdir.toString()) else emptyMap(),
        )
}
