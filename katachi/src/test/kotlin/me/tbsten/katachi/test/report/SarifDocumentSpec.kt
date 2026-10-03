package me.tbsten.katachi.test.report

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.report.internal.GITHUB_MAX_RESULTS
import me.tbsten.katachi.report.toSarif

/**
 * `toSarif` against a golden: every violation type katachi writes, two from outside, over a
 * fake repository one level below its git root. The golden is the specification of the shape;
 * the assertions below pin the parts of it a reader is most likely to get wrong by hand.
 */
class SarifDocumentSpec : FreeSpec({
    val tree = sarifRepository()
    val violations = everyKindOfViolation()
    val sarif = violations.toSarif(tree.context())

    "golden" - {
        "全種類の違反を含む文書が golden と一致する" {
            checkGolden("every-kind.sarif", sarif)
        }

        "0 件でも results が空の正しい文書になり、golden と一致する" {
            val empty = emptyList<Violation>().toSarif(tree.context(repositoryPrefix = ""))
            checkGolden("empty.sarif", empty)
            empty shouldContain "\"results\": []"
            empty shouldContain "\"rules\": []"
            empty shouldContain "\"uri\": \"file:///w/repo/android/\""
        }

        "同じ入力からは同じ出力になる" {
            violations.toSarif(tree.context()) shouldBe sarif
        }
    }

    "骨組み" - {
        "SARIF 2.1.0 を名乗り、run は 1 つ、tool は katachi" {
            sarif shouldContain "\"\$schema\": \"https://json.schemastore.org/sarif-2.1.0.json\""
            sarif shouldContain "\"version\": \"2.1.0\""
            sarif shouldContain "\"name\": \"katachi\""
            sarif shouldContain "\"semanticVersion\": \"0.0.0-test\""
            sarif shouldContain "\"informationUri\": \"https://tbsten.github.io/katachi/\""
            sarif.lines().count { it == "    {" } shouldBe 1
        }

        "2 スペースのインデント、LF、末尾の改行 1 つ" {
            sarif shouldEndWith "}\n"
            sarif shouldNotContain "\r"
            sarif shouldNotContain "\t"
            sarif.lines().filter { it.isNotEmpty() }.all { line -> (line.length - line.trimStart().length) % 2 == 0 } shouldBe true
        }

        "時刻や指紋のような決定的でない値を書かない" {
            sarif shouldNotContain "startTimeUtc"
            sarif shouldNotContain "endTimeUtc"
            sarif shouldNotContain "partialFingerprints"
            sarif shouldNotContain "automationDetails"
            sarif shouldNotContain "baselineState"
            sarif shouldNotContain "\"kind\": \"fail\""
        }
    }

    "rules" - {
        "出てきた label だけが id の辞書順に並び、katachi の違反は katachi. を前に付ける" {
            val ids = Regex("\"id\": \"([^\"]+)\"").findAll(sarif).map { it.groupValues[1] }.toList()
            ids shouldContainExactly listOf(
                "BareRule",
                "TodoRule",
                "katachi.AmbiguousLayout",
                "katachi.MissingDescription",
                "katachi.MissingFile",
                "katachi.StaleBaselineEntry",
                "katachi.UncheckedCheck",
                "katachi.UncheckedDirectory",
                "katachi.UncheckedFile",
                "katachi.UncheckedFileConstraint",
                "katachi.UnexpectedDirectory",
                "katachi.UnexpectedFile",
                "katachi.UnsatisfiedFileConstraint",
            )
        }

        "Warning の違反は level が warning、Error は error" {
            val levels = Regex("\"level\": \"(\\w+)\"").findAll(sarif).map { it.groupValues[1] }.toList()
            // 13 rules + 13 results; the three warnings (BareRule, AmbiguousLayout, MissingDescription) twice each.
            levels.count { it == "warning" } shouldBe 6
            levels.count { it == "error" } shouldBe 20
        }
    }

    "message" - {
        "テキストレポートの 1 ブロックから先頭行とインデント 1 段を外したもの" {
            val block = listOf(violations.first()).report().substringAfter("\n\n").lines()
            block.first() shouldBe "[UnexpectedFile] app/src/main/kotlin/com/example/Helper.kt"
            val expected = block.drop(1).map { it.removePrefix("  ") }.joinToString("\\n").replace("\"", "\\\"")
            sarif shouldContain "\"text\": \"$expected\""
            sarif shouldContain "\"text\": \"No file has been created yet for role domain.UseCase.\\nDeclared at: UseCaseRole.kt:16\\n\\nHow to fix:"
            // The copyable fragment keeps the indent it has below `How to fix:`, one step shallower.
            sarif shouldContain "\\n  - Add a new role for it:\\n      \\\"Helper\\\" {\\n        summary = \\\"TODO\\\"\\n"
        }

        "details の無い外部の違反は先頭行がそのまま message になる" {
            sarif shouldContain "\"text\": \"[BareRule] app/src/main/kotlin/com/example/Broken.kt\""
        }

        "非 ASCII の制約名はエスケープせずそのまま書く" {
            sarif shouldContain "FileConstraint: \\\"外から使えること\\\""
        }
    }

    "GitHub の上限" - {
        val path = "app/src/main/kotlin/com/example/Helper.kt"

        "25,001 件なら results は 25,000 件で、Error が先に残り、省いた件数を notification に書く" {
            val many = List(GITHUB_MAX_RESULTS) { ForeignSarifViolation(path, "Many", severity = Severity.Warning) } +
                ForeignSarifViolation(path, "Last", severity = Severity.Error)
            val document = many.toSarif(tree.context())
            Regex("\"ruleIndex\": ").findAll(document).count() shouldBe GITHUB_MAX_RESULTS
            document shouldContain "\"ruleId\": \"Last\""
            // The rules list is built from what was kept, so `Many` is still there and `Last` with it.
            Regex("\"id\": \"(Many|Last)\"").findAll(document).count() shouldBe 2
            document shouldContain
                "\"text\": \"1 result was left out: GitHub code scanning accepts at most 25000 results per run, " +
                "and refuses the whole file past that. The error-level results were kept first, then the warnings. " +
                "Hold the known violations back with a baseline (-Dkatachi.baseline.update) to bring the count down.\""
        }

        "25,000 件ちょうどなら全件そのままで、notification も無い" {
            val exact = List(GITHUB_MAX_RESULTS) { ForeignSarifViolation(path, "Many", severity = Severity.Warning) }
            val document = exact.toSarif(tree.context())
            Regex("\"ruleIndex\": ").findAll(document).count() shouldBe GITHUB_MAX_RESULTS
            document shouldNotContain "toolExecutionNotifications"
        }
    }

    "properties" - {
        "kind / severity / path は全部に、role と declaredAt は宣言を持つものだけに付く" {
            sarif shouldContain """
                |          "properties": {
                |            "kind": "Missing",
                |            "severity": "Error",
                |            "path": "core/domain/src/main/kotlin/com/example/domain/GetOrderUseCase.kt",
                |            "role": "domain.UseCase",
                |            "declaredAt": "UseCaseRole.kt:16"
                |          }
            """.trimMargin()
            sarif shouldContain """
                |          "properties": {
                |            "kind": "Unexpected",
                |            "severity": "Error",
                |            "path": "app/src/main/kotlin/com/example/Helper.kt"
                |          }
            """.trimMargin()
            sarif shouldContain "\"role\": \"domain.UseCase,domain.Repository\""
            sarif shouldContain "\"check\": \"com.example.TodoCheck\""
            sarif shouldContain "\"baseline\": \"katachi-baseline.json\""
        }
    }
})
