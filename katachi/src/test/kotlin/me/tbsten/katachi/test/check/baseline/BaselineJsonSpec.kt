package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.KatachiInvalidBaselineFileException
import me.tbsten.katachi.check.KatachiInvalidBaselineFileException.Problem
import me.tbsten.katachi.check.internal.BaselineKey
import me.tbsten.katachi.check.internal.BaselineLedger
import me.tbsten.katachi.check.internal.parseBaseline
import me.tbsten.katachi.check.internal.renderBaseline

private fun parse(text: String): Map<BaselineKey, Int> = parseBaseline(text, BASELINE_URI).entries

private fun invalid(text: String): KatachiInvalidBaselineFileException =
    shouldThrow<KatachiInvalidBaselineFileException> { parseBaseline(text, BASELINE_URI) }

private fun entryWith(fields: String): String = """{"version": 1, "checks": {"a.Check": [{$fields}]}}"""

private val oneEntry: Map<BaselineKey, Int> = mapOf(BaselineKey(check = "a.Check", rule = "X", path = "a.kt") to 1)

class BaselineJsonSpec : FreeSpec({
    "読めるもの" - {
        "先頭の BOM は読み飛ばす" {
            parse("\uFEFF" + entryWith(""""rule": "X", "path": "a.kt"""")) shouldBe oneEntry
        }

        "CRLF の改行でも読め、壊れた行は CRLF のまま数える" {
            val text = renderBaseline(BaselineLedger(oneEntry)).replace("\n", "\r\n")
            parse(text) shouldBe oneEntry

            val broken = "{\r\n  \"version\": 1,\r\n  \"checks\": {\r\n    \"a.Check\": [\r\n      {\"rule\": \"X\"}\r\n    ]\r\n  }\r\n}\r\n"
            invalid(broken).line shouldBe 5
        }

        "\\u エスケープはサロゲートペアも含めて読む" {
            val entries = parse(entryWith(""""rule": "X", "path": "日本/😀.kt""""))
            entries.keys.single().path shouldBe "日本/😀.kt"
        }

        "制御文字を含むパスは \\u で書き、読み戻せる" {
            val odd = mapOf(BaselineKey(check = "a.Check", rule = "X", path = "a\u0001b.kt") to 1)
            val text = renderBaseline(BaselineLedger(odd))
            text shouldContain "a\\u0001b.kt"
            parse(text) shouldBe odd
        }
    }

    "読めないもの" - {
        "1つのオブジェクトに同じ名前が2回あるとエラーにする" {
            val failure = invalid(entryWith(""""rule": "X", "path": "a.kt", "count": 2, "count": 3"""))
            failure.problem shouldBe Problem.DuplicateField
            failure.detail shouldBe "count"
            failure.message!! shouldContain "\"count\""
            failure.message!! shouldContain "twice"
        }

        "知らないフィールドはエラーにする" {
            val unknown = invalid(entryWith(""""rule": "X", "path": "a.kt", "severity": "Error""""))
            unknown.problem shouldBe Problem.UnknownField
            unknown.message!! shouldContain "\"severity\""
            invalid("""{"version": 1, "checks": {}, "comment": "x"}""").message!! shouldContain "\"comment\""
        }

        "入れ子が深すぎるとスタックを使い切る前にエラーにする" {
            val deep = "[".repeat(10_000) + "]".repeat(10_000)
            val failure = invalid("""{"version": 1, "checks": {"a.Check": $deep}}""")
            failure.problem shouldBe Problem.NotJson
            failure.message!! shouldContain "nested"
        }

        "ASCII 以外の数字は数として読まない" {
            invalid(entryWith(""""rule": "X", "path": "a.kt", "count": ١""")).problem shouldBe Problem.NotJson
        }

        "JSON の数の形でないものは数として読まない" {
            for (number in listOf("01", "1.", "-", "1e", "+1", ".5")) {
                invalid(entryWith(""""rule": "X", "path": "a.kt", "count": $number""")).problem shouldBe Problem.NotJson
            }
        }

        "件数が Int に収まらないときは大きすぎると言う" {
            val failure = invalid(entryWith(""""rule": "X", "path": "a.kt", "count": 99999999999"""))
            failure.problem shouldBe Problem.WrongType
            failure.message!! shouldContain "too large"
            failure.message!! shouldNotContain "whole number"
        }

        "壊れた \\u エスケープは JSON として読めないと言う" {
            invalid(entryWith(""""rule": "X", "path": "\u12"""")).problem shouldBe Problem.NotJson
        }
    }

    "エラーの文面" - {
        "トップレベルの欠落は「項目に無い」とは言わない" {
            val failure = invalid("""{"checks": {}}""")
            failure.problem shouldBe Problem.MissingField
            failure.message!! shouldContain "\"version\""
            failure.message!! shouldNotContain "entry"
        }

        "同じ項目が2回出たときは、先に出た行から順に言う" {
            val failure = invalid(
                """
                {"version": 1, "checks": {"a.Check": [
                  {"rule": "X", "path": "a"},
                  {"rule": "X", "path": "a"}
                ]}}
                """.trimIndent(),
            )
            failure.message!! shouldContain "at line 2 and again at line 3"
        }

        "マージの衝突マーカーがあれば、そう言って直し方を示す" {
            val failure = invalid(
                """
                {
                  "version": 1,
                  "checks": {
                    "a.Check": [
                <<<<<<< HEAD
                      {"rule": "X", "path": "a"}
                =======
                      {"rule": "X", "path": "b"}
                >>>>>>> feature
                    ]
                  }
                }
                """.trimIndent(),
            )
            failure.line shouldBe 5
            failure.problem shouldBe Problem.MergeConflict
            failure.message!! shouldContain "merge conflict"
            failure.message!! shouldContain "-Dkatachi.baseline.update=true"
        }
    }
})
