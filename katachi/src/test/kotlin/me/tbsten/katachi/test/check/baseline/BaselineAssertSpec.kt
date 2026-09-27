package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.KatachiBaselineNotFoundException
import me.tbsten.katachi.check.KatachiBaselineUpdateInCiException
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.KatachiInvalidBaselinePathException
import me.tbsten.katachi.dsl.baselineFile
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

/** A definition that allows `README.md` and nothing else, with or without a baseline. */
private fun definition(withBaseline: Boolean = true): Architecture = architectureOf {
    if (withBaseline) baseline = baselineFile()
    "Readme" { layout { "README.md".file() } }
}

/** A tree holding the allowed file plus [extra] files nothing allows. */
private fun treeWith(vararg extra: String): FakeFileSystem = repositoryOf {
    "README.md"()
    for (path in extra) path()
}

private fun Architecture.run(
    tree: FakeFileSystem,
    environment: BaselineEnvironment,
    vararg checks: ArchitectureProcessor<Unit, List<Violation>>,
    maxViolations: Int = 10,
): List<Violation> = assertWith(tree, checks.toList(), maxViolations, environment)

private fun Architecture.failure(
    tree: FakeFileSystem,
    environment: BaselineEnvironment,
    vararg checks: ArchitectureProcessor<Unit, List<Violation>>,
    maxViolations: Int = 10,
): KatachiArchitectureAssertionError =
    shouldThrow<KatachiArchitectureAssertionError> { run(tree, environment, *checks, maxViolations = maxViolations) }

class BaselineAssertSpec : FreeSpec({
    "baseline を指定しないとき" - {
        "validate と assert の結果が今までと同じで、update を指定しても何も書かない" {
            val tree = treeWith("notes.md", "legacy/Old.kt")
            val store = MemoryBaselineStore()
            val plain = definition(withBaseline = false)

            val before = plain.validate(tree).map { "[${it.label}] ${it.path}" }
            val normal = plain.failure(tree, environmentOf(store))
            val updating = plain.failure(tree, environmentOf(store, update = true))

            before shouldContainExactly listOf("[UnexpectedDirectory] legacy", "[UnexpectedFile] notes.md")
            normal.message shouldBe updating.message
            normal.message shouldNotContain "Baseline"
            store.writes shouldBe 0
        }
    }

    "ファイルが無いとき" - {
        "普段の実行では KatachiBaselineNotFoundException になり、場所と作るコマンドを示す" {
            val failure = shouldThrow<KatachiBaselineNotFoundException> {
                definition().run(treeWith("notes.md"), environmentOf(MemoryBaselineStore()))
            }
            failure.message shouldContain BASELINE_URI
            failure.message shouldContain "-Dkatachi.baseline.update=true"
        }

        "update では新しく作られ、テストは緑で、中身は決まった形になる" {
            val store = MemoryBaselineStore()
            val err = mutableListOf<String>()

            definition().run(treeWith("notes.md", "legacy/Old.kt"), environmentOf(store, update = true, standardError = err))

            store.files[BASELINE_FILE] shouldBe """
                {
                  "version": 1,
                  "checks": {
                    "me.tbsten.katachi.check.LayoutCheck": [
                      {"rule": "UnexpectedDirectory", "path": "legacy"},
                      {"rule": "UnexpectedFile", "path": "notes.md"}
                    ]
                  }
                }
            """.trimIndent() + "\n"
            err.joinToString("\n") shouldContain "Katachi baseline $BASELINE_URI: 2 violations recorded in 2 entries (+2, -0)."
        }
    }

    "突き合わせ" - {
        "既存の違反だけなら通り、棚上げした件数を stderr に出す" {
            val store = MemoryBaselineStore()
            val tree = treeWith("notes.md", "legacy/Old.kt")
            definition().run(tree, environmentOf(store, update = true))
            val err = mutableListOf<String>()

            val result = definition().run(tree, environmentOf(store, standardError = err))

            result.shouldBeEmpty()
            err shouldContainExactly listOf("Baseline $BASELINE_URI held back 2 violations.")
        }

        "新しい違反が1件あると、その1件だけが報告されて落ち、棚上げの件数の行も出る" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))

            val failure = definition().failure(treeWith("notes.md", "new.md"), environmentOf(store))

            failure.violations.map { "[${it.label}] ${it.path}" } shouldContainExactly listOf("[UnexpectedFile] new.md")
            failure.message!!.lines().first() shouldBe "Katachi check failed: 1 violation (Unexpected: 1)"
            failure.message shouldNotContain "notes.md"
            failure.message!!.lines().last() shouldBe "Baseline $BASELINE_URI held back 1 violation."
        }

        "改名・移動するとパスが変わり、新しい違反と stale が1件ずつ出る" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))

            val failure = definition().failure(treeWith("docs.md"), environmentOf(store))

            failure.violations.map { "[${it.label}] ${it.path}" } shouldContainExactly listOf(
                "[UnexpectedFile] docs.md",
                "[StaleBaselineEntry] notes.md",
            )
        }

        "台帳にある UnexpectedDirectory の下にファイルを足しても通る（既知の穴）" {
            // TODO: v0.3 may hold the number of files below such a directory in the entry. This
            //  spec pins today's behaviour so that change shows up here.
            val store = MemoryBaselineStore()
            definition().run(treeWith("legacy/Old.kt"), environmentOf(store, update = true))

            definition().run(treeWith("legacy/Old.kt", "legacy/New.kt", "legacy/deep/Newer.kt"), environmentOf(store))
                .shouldBeEmpty()
        }

        "assertNoErrors が返すのは突き合わせたあとの Warning だけ" {
            val store = MemoryBaselineStore()
            val check = ScriptedCheck(listOf(ForeignViolation("a.kt"), ForeignViolation("w.kt", severity = Severity.Warning)))
            definition().run(treeWith(), environmentOf(store, update = true), check)

            val result = definition().run(treeWith(), environmentOf(store), check)

            result.map { "[${it.label}] ${it.path}" } shouldContainExactly listOf("[TodoRule] w.kt")
        }
    }

    "stale と prune" - {
        "解消済みの項目は StaleBaselineEntry の Error で落ちる" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md", "todo.md"), environmentOf(store, update = true))

            val failure = definition().failure(treeWith("notes.md"), environmentOf(store))

            val stale = failure.violations.filterIsInstance<StaleBaselineEntry>().single()
            stale.kind shouldBe ViolationKind.Stale
            stale.severity shouldBe Severity.Error
            stale.label shouldBe "StaleBaselineEntry"
            stale.path shouldBe "todo.md"
            stale.allowed shouldBe 1
            stale.found shouldBe 0
            failure.message shouldContain "-Dkatachi.baseline.prune=true"
        }

        "prune で解消済みが消え、新しい違反は足されない" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md", "todo.md"), environmentOf(store, update = true))

            // Fixed todo.md, and added new.md in the same change.
            val failure = definition().failure(treeWith("notes.md", "new.md"), environmentOf(store, prune = true))

            failure.violations.map { "[${it.label}] ${it.path}" } shouldContainExactly listOf("[UnexpectedFile] new.md")
            store.files[BASELINE_FILE]!! shouldNotContain "todo.md"
            store.files[BASELINE_FILE]!! shouldNotContain "new.md"
            store.files[BASELINE_FILE]!! shouldContain "notes.md"
        }

        "prune のあとは緑になる" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md", "todo.md"), environmentOf(store, update = true))
            definition().run(treeWith("notes.md"), environmentOf(store, prune = true))

            definition().run(treeWith("notes.md"), environmentOf(store)).shouldBeEmpty()
        }

        "全部消えても空の checks としてファイルは残り、定義から外せると知らせる" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))
            val err = mutableListOf<String>()

            definition().run(treeWith(), environmentOf(store, prune = true, standardError = err))

            store.files[BASELINE_FILE]!! shouldContain "\"checks\": {}"
            err.joinToString("\n") shouldContain "baseline ="
        }

        "中身が変わらなければファイルに書かない" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))
            val writes = store.writes

            definition().run(treeWith("notes.md"), environmentOf(store, update = true))
            definition().run(treeWith("notes.md"), environmentOf(store, prune = true))

            store.writes shouldBe writes
        }
    }

    "部分的な結果" - {
        "update の実行に Failed があると、ファイルに書かずに失敗する" {
            val store = MemoryBaselineStore()

            val failure = definition().failure(treeWith("notes.md"), environmentOf(store, update = true), ThrowingScriptedCheck())

            failure.violations.map { it.label } shouldContainExactly listOf("UnexpectedFile", "UncheckedCheck")
            store.writes shouldBe 0
            store.files[BASELINE_FILE].shouldBeNull()
        }
    }

    "check の持ち物" - {
        "別々の check の組で update しても互いの項目を消さない" {
            val store = MemoryBaselineStore()
            val scripted = ScriptedCheck(listOf(ForeignViolation("a.kt")))
            definition().run(treeWith("notes.md"), environmentOf(store, update = true), scripted)
            val err = mutableListOf<String>()

            // Only the layout check runs this time, and the project changed in the meantime.
            definition().run(treeWith("todo.md"), environmentOf(store, update = true, standardError = err))

            val text = store.files[BASELINE_FILE]!!
            text shouldContain "\"path\": \"a.kt\""
            text shouldContain "\"path\": \"todo.md\""
            text shouldNotContain "notes.md"
            err.joinToString("\n") shouldContain "Kept 1 entry of checks that did not run: $SCRIPTED"
        }

        "stale の判定は走った check の分だけ" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true), ScriptedCheck(listOf(ForeignViolation("a.kt"))))

            // ScriptedCheck does not run, so its entry is neither matched nor stale.
            definition().run(treeWith("notes.md"), environmentOf(store)).shouldBeEmpty()
            // It runs and finds nothing: now its entry is stale.
            definition().failure(treeWith("notes.md"), environmentOf(store), ScriptedCheck())
                .violations.map { "[${it.label}] ${it.path}" } shouldContainExactly listOf("[StaleBaselineEntry] a.kt")
        }
    }

    "CI" - {
        "CI=true で update を指定すると KatachiBaselineUpdateInCiException" {
            shouldThrow<KatachiBaselineUpdateInCiException> {
                definition().run(treeWith("notes.md"), environmentOf(MemoryBaselineStore(), update = true, ci = true))
            }.message shouldContain "katachi.baseline.update"
        }

        "CI=true で prune を指定すると KatachiBaselineUpdateInCiException" {
            shouldThrow<KatachiBaselineUpdateInCiException> {
                definition().run(treeWith("notes.md"), environmentOf(MemoryBaselineStore(), prune = true, ci = true))
            }.message shouldContain "katachi.baseline.prune"
        }

        "CI=true でも普段の実行はそのまま突き合わせる" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))

            definition().run(treeWith("notes.md"), environmentOf(store, ci = true)).shouldBeEmpty()
        }
    }

    "パスの検証" - {
        for (path in listOf("", "/abs/katachi-baseline.json", "C:/katachi-baseline.json", "../katachi-baseline.json", "a/../b.json", "config\\baseline.json", "./katachi-baseline.json", "config//baseline.json", "config/", ".", "config/./baseline.json")) {
            "\"$path\" は定義を評価した時点で KatachiInvalidBaselinePathException になる" {
                val failure = shouldThrow<KatachiInvalidBaselinePathException> {
                    architectureOf { baseline = baselineFile(path) }
                }
                failure.path shouldBe path
                failure.message shouldContain "BaselineAssertSpec.kt"
            }
        }

        "既定のパスは katachi-baseline.json で、相対パスはそのまま持つ" {
            architectureOf { baseline = baselineFile() }.baseline!!.path shouldBe "katachi-baseline.json"
            architectureOf { baseline = baselineFile("config/katachi/baseline.json") }.baseline!!.path shouldBe
                "config/katachi/baseline.json"
            architectureOf { }.baseline.shouldBeNull()
        }
    }

    "台帳ファイルの宣言" - {
        "update のとき、台帳ファイルがどの layout にも無ければ警告する" {
            val err = mutableListOf<String>()
            definition().run(treeWith("notes.md"), environmentOf(MemoryBaselineStore(), update = true, standardError = err))

            err.joinToString("\n") shouldContain "UnexpectedFile"
        }

        "宣言してあれば警告しない" {
            val err = mutableListOf<String>()
            val declared = architectureOf {
                baseline = baselineFile()
                "Readme" { layout { "README.md".file() } }
                "Baseline" { layout { "katachi-baseline.json".file() } }
            }
            declared.run(treeWith("notes.md"), environmentOf(MemoryBaselineStore(), update = true, standardError = err))

            err.joinToString("\n") shouldNotContain "UnexpectedFile"
        }
    }

    "報告の見た目" - {
        "棚上げの件数・超過・stale のブロックが決まった形で、打ち切りの行より後ろに出る" {
            val store = MemoryBaselineStore(
                BASELINE_FILE to """
                    {
                      "version": 1,
                      "checks": {
                        "me.tbsten.katachi.check.LayoutCheck": [
                          {"rule": "UnexpectedFile", "path": "gone.md", "count": 2},
                          {"rule": "UnexpectedFile", "path": "held.md"}
                        ],
                        "$SCRIPTED": [
                          {"rule": "TodoRule", "path": "x.kt"}
                        ]
                      }
                    }
                """.trimIndent() + "\n",
            )
            val check = ScriptedCheck(List(2) { ForeignViolation("x.kt") })

            val message = definition()
                .failure(treeWith("held.md", "n1.md", "n2.md"), environmentOf(store), check, maxViolations = 3)
                .message!!

            message.lines().first() shouldBe
                "Katachi check failed: 5 violations (Unexpected: 2, FileConstraint: 2, Stale: 1)"
            message shouldContain """
                [StaleBaselineEntry] file:///repo/gone.md
                  Baseline: file:///repo/katachi-baseline.json (me.tbsten.katachi.check.LayoutCheck)
                  Rule: UnexpectedFile, allowed 2, found 0

                  How to fix:
                    - Every violation this entry held back is gone. Remove the entry by re-running the architecture test with
                        -Dkatachi.baseline.prune=true
            """.trimIndent()
            message shouldNotContain "held.md"
            message.endsWith(
                """
                Showing first 3 (2 more: Unexpected 1, FileConstraint 1)

                Baseline file:///repo/katachi-baseline.json held back 1 violation.
                2 violations reported in full because their baseline entries allow fewer.
                """.trimIndent(),
            ) shouldBe true
        }

        "件数が減った stale のブロックは減った件数と prune を示す" {
            val store = MemoryBaselineStore(
                BASELINE_FILE to """
                    {"version": 1, "checks": {"me.tbsten.katachi.check.LayoutCheck": [{"rule": "UnexpectedFile", "path": "n1.md", "count": 3}]}}
                """.trimIndent(),
            )

            val message = definition().failure(treeWith("n1.md"), environmentOf(store)).message!!

            message shouldContain listOf(
                "  Rule: UnexpectedFile, allowed 3, found 1",
                "",
                "  How to fix:",
                "    - 2 violations this entry held back are gone. Shrink the entry by re-running the architecture test with",
                "        -Dkatachi.baseline.prune=true",
            ).joinToString("\n")
        }
    }
})
