package me.tbsten.katachi.test.report

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.report.internal.SarifEnvironment
import me.tbsten.katachi.report.internal.numberedSarifPath
import me.tbsten.katachi.report.writeSarif
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

/** A definition that allows `README.md` and nothing else. */
private fun definition(): Architecture = architectureOf {
    "Readme".role { layout { "README.md".file() } }
}

/** The fake repository `assertWith` walks, at `/repo`, with [extra] files nothing allows. */
private fun repository(vararg extra: String): FakeFileSystem = repositoryOf {
    "README.md"()
    for (path in extra) path()
}

private fun Architecture.run(tree: FakeFileSystem, sarif: SarifEnvironment): List<Violation> =
    assertWith(tree, emptyList(), 10, BaselineEnvironment(standardError = { }), sarif)

/**
 * The write half: numbering, within one JVM and across the test JVMs of one Test task, the real
 * disk, and the way `assertWith` writes -- only when asked, before it throws, and never failing
 * the check over it.
 */
class SarifWriteSpec : FreeSpec({
    "連番" - {
        "1 回目はそのまま、2 回目から -2, -3 を拡張子の前に付ける" {
            val file = FsPath.of("/repo/build/reports/katachi/katachi.sarif")
            numberedSarifPath(file, 1) shouldBe file
            numberedSarifPath(file, 2).value shouldBe "/repo/build/reports/katachi/katachi-2.sarif"
            numberedSarifPath(file, 3).value shouldBe "/repo/build/reports/katachi/katachi-3.sarif"
            numberedSarifPath(FsPath.of("/repo/report"), 2).value shouldBe "/repo/report-2"
            numberedSarifPath(FsPath.of("katachi.sarif"), 2).value shouldBe "katachi-2.sarif"
        }

        "同じパスに 2 回書くと 2 回目は連番で、標準エラーに注意が出る" {
            val tree = FakeSarifTree()
            val err = mutableListOf<String>()
            val sarif = "/repo/build/reports/katachi/katachi.sarif"

            repeat(2) {
                shouldThrow<KatachiArchitectureAssertionError> {
                    definition().run(repository("notes.md"), tree.environment(sarif = sarif, standardError = err, prefix = null))
                }
            }

            tree.writes.map { it.first.value } shouldContainExactly listOf(
                "/repo/build/reports/katachi/katachi.sarif",
                "/repo/build/reports/katachi/katachi-2.sarif",
            )
            err.single() shouldBe
                "Warning: file:///repo/build/reports/katachi/katachi.sarif already exists, so this run was written to " +
                "file:///repo/build/reports/katachi/katachi-2.sarif instead. Either another assert() of this run wrote " +
                "it, in this JVM or in another test JVM, or an earlier run left it and nothing deleted it before this " +
                "one. Upload every numbered file next to it too, or give each architecture test its own " +
                "-Dkatachi.report.sarif path."
        }

        "ほかの JVM が先に取った番号は上書きせず、空いている最初の番号に書く" {
            val tree = FakeSarifTree(
                "/repo/build/reports/katachi/katachi.sarif" to "another JVM",
                "/repo/build/reports/katachi/katachi-2.sarif" to "yet another JVM",
            )

            shouldThrow<KatachiArchitectureAssertionError> {
                definition().run(
                    repository("notes.md"),
                    tree.environment(sarif = "/repo/build/reports/katachi/katachi.sarif", prefix = null),
                )
            }

            tree.writes.single().first.value shouldBe "/repo/build/reports/katachi/katachi-3.sarif"
            tree.readText(FsPath.of("/repo/build/reports/katachi/katachi.sarif")) shouldBe "another JVM"
        }
    }

    "別々の test JVM" - {
        // What `forkEvery = 1` and `maxParallelForks = 2` make of one Test task: JVMs of their own, every one handed
        // the same -Dkatachi.report.sarif, and none of them knowing what the others wrote.
        "forkEvery = 1 のように順に走った 2 つの JVM の結果が、katachi.sarif と katachi-2.sarif に両方残る" {
            withTempSarifDirectory { directory ->
                val target = File(directory, "katachi.sarif")

                testJvm(extraFile = "first.md", target).finish()
                val second = testJvm(extraFile = "second.md", target).finish()

                directory.list().orEmpty().sorted() shouldContainExactly listOf("katachi-2.sarif", "katachi.sarif")
                File(directory, "katachi.sarif").readText() shouldContain "first.md"
                File(directory, "katachi-2.sarif").readText() shouldContain "second.md"
                second shouldContain "katachi.sarif already exists, so this run was written to"
            }
        }

        "maxParallelForks = 2 のように同時に走っても、どちらの結果も上書きされない" {
            withTempSarifDirectory { directory ->
                val target = File(directory, "katachi.sarif")

                listOf(testJvm(extraFile = "first.md", target), testJvm(extraFile = "second.md", target))
                    .forEach { it.finish() }

                directory.list().orEmpty().sorted() shouldContainExactly listOf("katachi-2.sarif", "katachi.sarif")
                val reports = directory.listFiles().orEmpty().map { it.readText() }
                reports.count { "first.md" in it && "second.md" !in it } shouldBe 1
                reports.count { "second.md" in it && "first.md" !in it } shouldBe 1
            }
        }
    }

    "assertWith からの書き出し" - {
        "property があれば例外を投げる前に書き、違反の集合はテキストのレポートと同じ" {
            val tree = FakeSarifTree()
            val failure = shouldThrow<KatachiArchitectureAssertionError> {
                definition().run(repository("notes.md", "legacy/Old.kt"), tree.environment(sarif = "/repo/build/reports/katachi/katachi.sarif", prefix = ""))
            }

            val (path, text) = tree.writes.single()
            path.value shouldBe "/repo/build/reports/katachi/katachi.sarif"
            text shouldStartWith "{\n  \"\$schema\": \"https://json.schemastore.org/sarif-2.1.0.json\""
            text shouldContain "\"uri\": \"file:///repo/\""
            Regex("\"ruleId\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList() shouldContainExactly
                failure.violations.map { "katachi.${it.label}" }
            text shouldContain "\"path\": \"notes.md\""
            text shouldContain "\"path\": \"legacy\""
        }

        "相対パスは JVM の作業ディレクトリ基準で解決する" {
            val tree = FakeSarifTree()
            definition().run(repository(), tree.environment(sarif = "build/reports/katachi/katachi.sarif", prefix = ""))

            tree.writes.single().first.value shouldBe "/repo/build/reports/katachi/katachi.sarif"
        }

        "違反が 0 件でも results が空の文書を書く" {
            val tree = FakeSarifTree()
            definition().run(repository(), tree.environment(sarif = "/repo/katachi.sarif", prefix = ""))

            tree.writes.single().second shouldContain "\"results\": []"
        }

        "property が無ければ何も書かず、git にも聞かない" {
            val tree = FakeSarifTree()
            val calls = mutableListOf<FsPath>()
            val err = mutableListOf<String>()

            shouldThrow<KatachiArchitectureAssertionError> {
                definition().run(repository("notes.md"), tree.environment(sarif = null, prefixCalls = calls, standardError = err))
            }
            definition().run(repository(), tree.environment(sarif = "", prefixCalls = calls, standardError = err))

            tree.writes.shouldBeEmpty()
            calls.shouldBeEmpty()
            err.shouldBeEmpty()
        }

        "書けなかったときは標準エラーに理由を出し、検査の結果は変わらない" {
            val tree = FakeSarifTree()
            tree.unwritable += FsPath.of("/repo/out/katachi.sarif")
            val err = mutableListOf<String>()

            val failure = shouldThrow<KatachiArchitectureAssertionError> {
                definition().run(repository("notes.md"), tree.environment(sarif = "/repo/out/katachi.sarif", standardError = err, prefix = ""))
            }
            definition().run(repository(), tree.environment(sarif = "/repo/out/katachi.sarif", standardError = err, prefix = "")).shouldBeEmpty()

            failure.violations.map { it.path } shouldContainExactly listOf("notes.md")
            err shouldContainExactly List(2) {
                "Warning: katachi could not write the SARIF report to file:///repo/out/katachi.sarif : " +
                    "java.io.IOException: Permission denied: /repo/out/katachi.sarif . The check itself is not affected."
            }
        }

        "sourceRoots の property は File.pathSeparator で分け、無ければ src/<sourceSet>/{kotlin,java} を探す" {
            // A declared file that does not exist: its declaration site is this spec file, which no
            // fake tree below holds, so the notification names the source roots that were searched.
            val missing = architectureOf { "Readme".role { layout { "README.md".file(); "MISSING.md".file() } } }

            val given = FakeSarifTree("/repo/architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt" to "")
            shouldThrow<KatachiArchitectureAssertionError> {
                missing.run(
                    repository(),
                    given.environment(
                        sarif = "/repo/katachi.sarif",
                        sourceRoots = listOf("/repo/architecture-test/src/test/kotlin", "/repo/other"),
                        prefix = "",
                    ),
                )
            }
            given.writes.single().second shouldContain
                "was not found under any source root (architecture-test/src/test/kotlin, other)."

            val conventional = FakeSarifTree(
                "/repo/src/test/kotlin/Elsewhere.kt" to "",
                "/repo/src/main/java/Main.java" to "",
                "/repo/src/main/resources/ignored.txt" to "",
            )
            shouldThrow<KatachiArchitectureAssertionError> {
                missing.run(repository(), conventional.environment(sarif = "/repo/katachi.sarif", prefix = ""))
            }
            conventional.writes.single().second shouldContain
                "was not found under any source root (src/main/java, src/test/kotlin)."
        }
    }

    "実際のディスク" - {
        "writeSarif は親ディレクトリを作り、UTF-8 で書き、書いたパスを返す" {
            val directory = Files.createTempDirectory("katachi-sarif").toFile()
            try {
                val target = FsPath.of(File(directory, "reports/katachi/katachi.sarif").path)
                val written = everyKindOfViolation().writeSarif(target, sarifRepository().context())

                written shouldBe target
                val bytes = File(written.value).readBytes()
                bytes.toString(Charsets.UTF_8) shouldContain "外から使えること"
                // No BOM.
                (bytes[0].toInt() and 0xFF) shouldBe '{'.code
            } finally {
                directory.deleteRecursively()
            }
        }

        "writeSarif は既にあるファイルを上書きせず、次の連番に書いてそのパスを返す" {
            withTempSarifDirectory { directory ->
                val taken = File(directory, "katachi.sarif").apply { writeText("another JVM") }

                val written = everyKindOfViolation().writeSarif(FsPath.of(taken.path), sarifRepository().context())

                written.value shouldBe File(directory, "katachi-2.sarif").path
                taken.readText() shouldBe "another JVM"
                File(written.value).readText() shouldContain "外から使えること"
            }
        }
    }
})

/** A directory of the spec's own on the real disk, gone again however [block] ends. */
private fun withTempSarifDirectory(block: (File) -> Unit) {
    val directory = Files.createTempDirectory("katachi-sarif").toFile()
    try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}

/** A JVM of its own running [SarifTestJvm] over a repository holding [extraFile], as a Test task forks one. */
private fun testJvm(extraFile: String, target: File): Process =
    ProcessBuilder(
        File(System.getProperty("java.home"), "bin/java").path,
        "-Dkatachi.report.sarif=${target.path}",
        "-cp",
        System.getProperty("java.class.path"),
        SarifTestJvm::class.java.name,
        extraFile,
    ).redirectErrorStream(true).start()

/** Waits for this JVM, which must end by itself with status 0, and answers with what it printed. */
private fun Process.finish(): String {
    val printed = inputStream.bufferedReader().readText()
    withClue(printed) {
        waitFor(1, TimeUnit.MINUTES) shouldBe true
        exitValue() shouldBe 0
    }
    return printed
}
