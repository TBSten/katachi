package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.KatachiInvalidBaselineFileException
import me.tbsten.katachi.check.KatachiUnsupportedBaselineVersionException
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.UnexpectedDirectory
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineKey
import me.tbsten.katachi.check.internal.BaselineLedger
import me.tbsten.katachi.check.internal.CheckedViolations
import me.tbsten.katachi.check.internal.baselineKeyOf
import me.tbsten.katachi.check.internal.parseBaseline
import me.tbsten.katachi.check.internal.prunedWith
import me.tbsten.katachi.check.internal.reconcile
import me.tbsten.katachi.check.internal.renderBaseline
import me.tbsten.katachi.check.internal.updatedWith
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.test.check.architectureOf

private val role: Role = architectureOf { "domain".group { "UseCase" { } } }.allRoles.single()

private fun unsatisfied(
    path: String = "core/Helper.kt",
    line: Int? = 3,
    declaration: String? = "Helper",
    constraint: String? = "is public",
): UnsatisfiedFileConstraint = UnsatisfiedFileConstraint(
    path = path,
    declaration = declaration,
    line = line,
    role = role,
    constraintName = constraint,
    layoutPath = null,
    declaredAt = DeclarationSite("UseCaseRole.kt", 12),
)

private fun layoutRun(vararg violations: Violation): CheckedViolations = CheckedViolations(LAYOUT, violations.toList())

private fun ledgerOf(vararg entries: Pair<BaselineKey, Int>): BaselineLedger = BaselineLedger(mapOf(*entries))

private fun layoutKey(rule: String, path: String): BaselineKey = BaselineKey(check = LAYOUT, rule = rule, path = path)

class BaselineLedgerSpec : FreeSpec({
    "キー" - {
        "UnsatisfiedFileConstraint は行が変わっても同じキーになる" {
            val before = baselineKeyOf("c.FileConstraintCheck", unsatisfied(line = 3))
            val after = baselineKeyOf("c.FileConstraintCheck", unsatisfied(line = 40))

            after shouldBe before
            before shouldBe BaselineKey(
                check = "c.FileConstraintCheck",
                rule = "UnsatisfiedFileConstraint",
                path = "core/Helper.kt",
                role = "domain.UseCase",
                constraint = "is public",
                declaration = "Helper",
            )
        }

        "行ずれだけなら台帳の項目に一致して棚上げされる" {
            val ledger = ledgerOf(baselineKeyOf("c.X", unsatisfied(line = 3))!! to 1)

            val result = ledger.reconcile(listOf(CheckedViolations("c.X", listOf(unsatisfied(line = 99)))), "katachi-baseline.json")

            result.reported.shouldBeEmpty()
            result.heldBack shouldBe 1
        }

        "Warning はキーを持たず、台帳にも突き合わせにも入らない" {
            val warning = ForeignViolation("app/A.kt", severity = Severity.Warning)

            baselineKeyOf(SCRIPTED, warning).shouldBeNull()
            BaselineLedger(emptyMap()).updatedWith(listOf(CheckedViolations(SCRIPTED, listOf(warning))))
                .entries shouldBe emptyMap()
            val result = BaselineLedger(emptyMap())
                .reconcile(listOf(CheckedViolations(SCRIPTED, listOf(warning))), "katachi-baseline.json")
            result.reported shouldContainExactly listOf(warning)
            result.heldBack shouldBe 0
        }

        "第三者の check の違反は check + label + パスで突き合わせる" {
            val key = baselineKeyOf(SCRIPTED, ForeignViolation("app/A.kt", label = "TodoRule"))
            key shouldBe BaselineKey(check = SCRIPTED, rule = "TodoRule", path = "app/A.kt")

            val ledger = ledgerOf(key!! to 1)
            ledger.reconcile(
                listOf(CheckedViolations(SCRIPTED, listOf(ForeignViolation("app/A.kt", label = "TodoRule")))),
                "katachi-baseline.json",
            ).reported.shouldBeEmpty()
            // Same path, another label: a different rule, so a new violation.
            ledger.reconcile(
                listOf(CheckedViolations(SCRIPTED, listOf(ForeignViolation("app/A.kt", label = "FixmeRule")))),
                "katachi-baseline.json",
            ).reported.map { it.label } shouldBe listOf("FixmeRule", "StaleBaselineEntry")
            // Same label and path, filed under another check: also not the entry.
            ledger.reconcile(
                listOf(
                    CheckedViolations(SCRIPTED, emptyList()),
                    CheckedViolations(OTHER_SCRIPTED, listOf(ForeignViolation("app/A.kt", label = "TodoRule"))),
                ),
                "katachi-baseline.json",
            ).reported.map { it.label } shouldBe listOf("TodoRule", "StaleBaselineEntry")
        }
    }

    "件数" - {
        val key = layoutKey("UnexpectedFile", "app/Util.kt")
        val ledger = ledgerOf(key to 2)
        fun found(count: Int) = layoutRun(*Array(count) { UnexpectedFile("app/Util.kt", emptyList()) })

        "台帳が2件で今が2件なら全部棚上げされる" {
            val result = ledger.reconcile(listOf(found(2)), "katachi-baseline.json")
            result.reported.shouldBeEmpty()
            result.heldBack shouldBe 2
        }

        "今が3件なら3件とも報告し、超過として数える" {
            val result = ledger.reconcile(listOf(found(3)), "katachi-baseline.json")
            result.reported.map { it.label } shouldBe List(3) { "UnexpectedFile" }
            result.exceeded shouldBe 3
            result.heldBack shouldBe 0
        }

        "今が1件なら1件は棚上げし、stale として allowed 2 / found 1 を出す" {
            val result = ledger.reconcile(listOf(found(1)), "katachi-baseline.json")
            result.heldBack shouldBe 1
            val stale = result.stale.single()
            stale.allowed shouldBe 2
            stale.found shouldBe 1
            stale.path shouldBe "app/Util.kt"
            stale.rule shouldBe "UnexpectedFile"
            stale.check shouldBe LAYOUT
            stale.baselinePath shouldBe "katachi-baseline.json"
        }

        "走らなかった check の項目は stale にも一致にもならない" {
            val result = ledgerOf(BaselineKey(SCRIPTED, "TodoRule", "a.kt") to 1)
                .reconcile(listOf(layoutRun()), "katachi-baseline.json")
            result.stale.shouldBeEmpty()
            result.heldBack shouldBe 0
        }
    }

    "update と prune" - {
        "update は走った check の項目だけを今の違反で置き換える" {
            val ledger = ledgerOf(
                layoutKey("UnexpectedFile", "old.kt") to 1,
                BaselineKey(SCRIPTED, "TodoRule", "a.kt") to 1,
            )
            val updated = ledger.updatedWith(listOf(layoutRun(UnexpectedFile("new.kt", emptyList()), UnexpectedFile("new.kt", emptyList()))))

            updated.entries shouldBe mapOf(
                layoutKey("UnexpectedFile", "new.kt") to 2,
                BaselineKey(SCRIPTED, "TodoRule", "a.kt") to 1,
            )
        }

        "prune は件数を今の件数まで下げ、0 になった項目を消し、新しい違反は足さない" {
            val ledger = ledgerOf(
                layoutKey("UnexpectedFile", "a.kt") to 3,
                layoutKey("UnexpectedFile", "gone.kt") to 1,
            )
            val pruned = ledger.prunedWith(
                listOf(layoutRun(UnexpectedFile("a.kt", emptyList()), UnexpectedFile("brand-new.kt", emptyList()))),
            )

            pruned.entries shouldBe mapOf(layoutKey("UnexpectedFile", "a.kt") to 1)
        }
    }

    "ファイルの形式" - {
        val ledger = ledgerOf(
            layoutKey("UnexpectedFile", "app/src/main/kotlin/Util.kt") to 2,
            layoutKey("UnexpectedDirectory", "legacy") to 1,
            BaselineKey(
                check = "me.tbsten.katachi.check.FileConstraintCheck",
                rule = "UnsatisfiedFileConstraint",
                path = "core/domain/useCase/Helper.kt",
                role = "domain.UseCase",
                constraint = "is public",
                declaration = "Helper",
            ) to 1,
        )
        val golden = """
            {
              "version": 1,
              "checks": {
                "me.tbsten.katachi.check.FileConstraintCheck": [
                  {"rule": "UnsatisfiedFileConstraint", "path": "core/domain/useCase/Helper.kt", "role": "domain.UseCase", "constraint": "is public", "declaration": "Helper"}
                ],
                "me.tbsten.katachi.check.LayoutCheck": [
                  {"rule": "UnexpectedFile", "path": "app/src/main/kotlin/Util.kt", "count": 2},
                  {"rule": "UnexpectedDirectory", "path": "legacy"}
                ]
              }
            }
        """.trimIndent() + "\n"

        "決まった並び・1行1項目・末尾に改行1つで書き出す" {
            renderBaseline(ledger) shouldBe golden
        }

        "入力の順番を入れ替えても書き出したバイト列が同じ" {
            val shuffled = BaselineLedger(ledger.entries.entries.reversed().associate { it.key to it.value })
            renderBaseline(shuffled) shouldBe renderBaseline(ledger)
            val updatedForward = BaselineLedger(emptyMap()).updatedWith(
                listOf(layoutRun(UnexpectedDirectory("b"), UnexpectedFile("a.kt", emptyList()), UnexpectedFile("a.kt", emptyList()))),
            )
            val updatedBackward = BaselineLedger(emptyMap()).updatedWith(
                listOf(layoutRun(UnexpectedFile("a.kt", emptyList()), UnexpectedDirectory("b"), UnexpectedFile("a.kt", emptyList()))),
            )
            renderBaseline(updatedForward) shouldBe renderBaseline(updatedBackward)
        }

        "空の台帳は checks を空のオブジェクトで書く" {
            renderBaseline(BaselineLedger(emptyMap())) shouldBe """
                {
                  "version": 1,
                  "checks": {}
                }
            """.trimIndent() + "\n"
        }

        "書き出したものを読むと同じ台帳に戻る" {
            parseBaseline(golden, BASELINE_URI).entries shouldBe ledger.entries
        }

        "引用符・バックスラッシュ・非 ASCII を含むパスも往復する" {
            val odd = ledgerOf(layoutKey("UnexpectedFile", "docs/\"quoted\" \\ 日本語.md") to 1)
            parseBaseline(renderBaseline(odd), BASELINE_URI).entries shouldBe odd.entries
        }
    }

    "壊れたファイル" - {
        fun invalid(text: String): KatachiInvalidBaselineFileException =
            shouldThrow<KatachiInvalidBaselineFileException> { parseBaseline(text, BASELINE_URI) }

        "JSON として読めない" {
            val failure = invalid("{\n  \"version\": 1,\n  \"checks\": {\n")
            failure.problem shouldBe KatachiInvalidBaselineFileException.Problem.NotJson
            failure.message shouldContain BASELINE_URI
            failure.message shouldContain "line"
        }

        "必須のフィールドが無い" {
            val failure = invalid(
                """
                {
                  "version": 1,
                  "checks": {
                    "a.Check": [
                      {"rule": "X"}
                    ]
                  }
                }
                """.trimIndent(),
            )
            failure.problem shouldBe KatachiInvalidBaselineFileException.Problem.MissingField
            failure.message shouldContain "\"path\""
            failure.message shouldContain "line 5"
        }

        "count が 0" {
            invalid(
                """
                {"version": 1, "checks": {"a.Check": [{"rule": "X", "path": "a", "count": 0}]}}
                """.trimIndent(),
            ).problem shouldBe KatachiInvalidBaselineFileException.Problem.CountBelowOne
        }

        "同じキーが2回出る" {
            invalid(
                """
                {"version": 1, "checks": {"a.Check": [{"rule": "X", "path": "a"}, {"rule": "X", "path": "a"}]}}
                """.trimIndent(),
            ).problem shouldBe KatachiInvalidBaselineFileException.Problem.DuplicateEntry
        }

        "版がこの katachi より新しい" {
            val failure = shouldThrow<KatachiUnsupportedBaselineVersionException> {
                parseBaseline("""{"version": 2, "checks": {}}""", BASELINE_URI)
            }
            failure.version shouldBe 2
            failure.message shouldContain "katachi"
        }
    }
})
