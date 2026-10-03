package me.tbsten.katachi.test.report

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.report.internal.SarifEnvironment
import me.tbsten.katachi.report.internal.SarifRuns
import me.tbsten.katachi.report.internal.numberedSarifPath
import me.tbsten.katachi.report.writeSarif
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

/** A definition that allows `README.md` and nothing else. */
private fun definition(): Architecture = architectureOf {
    "Readme" { layout { "README.md".file() } }
}

/** The fake repository `assertWith` walks, at `/repo`, with [extra] files nothing allows. */
private fun repository(vararg extra: String): FakeFileSystem = repositoryOf {
    "README.md"()
    for (path in extra) path()
}

private fun Architecture.run(tree: FakeFileSystem, sarif: SarifEnvironment): List<Violation> =
    assertWith(tree, emptyList(), 10, BaselineEnvironment(standardError = { }), sarif)

/**
 * The write half: numbering within one JVM, the real disk, and the way `assertWith` writes --
 * only when asked, before it throws, and never failing the check over it.
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

        "同じ JVM で同じパスに 2 回書くと 2 回目は連番で、標準エラーに注意が出る" {
            val tree = FakeSarifTree()
            val runs = SarifRuns()
            val err = mutableListOf<String>()
            val sarif = "/repo/build/reports/katachi/katachi.sarif"

            shouldThrow<KatachiArchitectureAssertionError> {
                definition().run(repository("notes.md"), tree.environment(sarif = sarif, runs = runs, standardError = err, prefix = null))
            }
            shouldThrow<KatachiArchitectureAssertionError> {
                definition().run(repository("notes.md"), tree.environment(sarif = sarif, runs = runs, standardError = err, prefix = null))
            }

            tree.writes.map { it.first.value } shouldContainExactly listOf(
                "/repo/build/reports/katachi/katachi.sarif",
                "/repo/build/reports/katachi/katachi-2.sarif",
            )
            err.single() shouldBe
                "Warning: an earlier assert() in this JVM already wrote file:///repo/build/reports/katachi/katachi.sarif , " +
                "so this run was written to file:///repo/build/reports/katachi/katachi-2.sarif instead. " +
                "Give each architecture test its own -Dkatachi.report.sarif path to keep them apart."
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
            val missing = architectureOf { "Readme" { layout { "README.md".file(); "MISSING.md".file() } } }

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
    }
})
