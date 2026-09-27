package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.KatachiBaselineCheckConflictException
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineRuns
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

private fun definition(): Architecture = architectureOf {
    baseline()
    "Readme" { layout { "README.md".file() } }
}

private fun treeWith(vararg extra: String): FakeFileSystem = repositoryOf {
    "README.md"()
    for (path in extra) path()
}

private fun ledgerOf(vararg paths: String): String =
    """{"version": 1, "checks": {"$SCRIPTED": [${paths.joinToString(", ") { """{"rule": "TodoRule", "path": "$it"}""" }}]}}"""

/** One test run: every call shares the same JVM's memory of what ran. */
private class TestRun(val store: MemoryBaselineStore = MemoryBaselineStore()) {
    val runs = BaselineRuns()

    fun assert(
        definition: Architecture,
        tree: FakeFileSystem,
        vararg checks: ArchitectureProcessor<Unit, List<Violation>>,
        update: Boolean = false,
        prune: Boolean = false,
    ): List<Violation> = definition.assertWith(tree, checks.toList(), 10, environmentOf(store, update = update, prune = prune, runs = runs))
}

class BaselineCheckConflictSpec : FreeSpec({
    "同じ check クラスを設定違いで別々の assert に渡すと" - {
        "update では後の assert が KatachiBaselineCheckConflictException で止まり、ファイルを書かない" {
            val run = TestRun()
            run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("a.kt"))), update = true)
            val written = run.store.files[BASELINE_FILE]
            val writes = run.store.writes

            val conflict = shouldThrow<KatachiBaselineCheckConflictException> {
                run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("b.kt"))), update = true)
            }

            run.store.writes shouldBe writes
            run.store.files[BASELINE_FILE] shouldBe written
            conflict.check shouldBe SCRIPTED
            conflict.file shouldBe BASELINE_URI
            conflict.message!! shouldContain "BaselineCheckConflictSpec.kt"
            conflict.message!! shouldContain "single assert(...)"
        }

        "prune でも後の assert が止まり、先の assert が消した項目をそれ以上消さない" {
            val run = TestRun(MemoryBaselineStore(BASELINE_FILE to ledgerOf("a.kt", "b.kt")))
            // The first run cannot know about the second, so it still prunes b.kt away.
            run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("a.kt"))), prune = true)
            val afterFirst = run.store.files[BASELINE_FILE]

            shouldThrow<KatachiBaselineCheckConflictException> {
                run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("b.kt"))), prune = true)
            }
            run.store.files[BASELINE_FILE] shouldBe afterFirst
        }

        "普段の実行でも後の assert が止まり、何が起きたかを言う" {
            val run = TestRun(MemoryBaselineStore(BASELINE_FILE to ledgerOf("a.kt", "b.kt")))
            // The first run reports the other's entry as stale: it cannot tell yet.
            shouldThrow<KatachiArchitectureAssertionError> {
                run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("a.kt"))))
            }.violations.map { it.label } shouldBe listOf("StaleBaselineEntry")

            shouldThrow<KatachiBaselineCheckConflictException> {
                run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("b.kt"))))
            }.message!! shouldContain "stale"
        }
    }

    "2つの定義が layout の違う形で1つの台帳を共有すると、LayoutCheck の衝突として止まる" {
        val run = TestRun()
        val other = architectureOf {
            baseline()
            "Notes" { layout { "notes.md".file() } }
        }
        run.assert(definition(), treeWith("notes.md"), update = true)

        shouldThrow<KatachiBaselineCheckConflictException> {
            run.assert(other, treeWith("notes.md"), update = true)
        }.check shouldBe LAYOUT
    }

    "衝突しないもの" - {
        "同じ結果の check を何度走らせても止まらない" {
            val run = TestRun()
            val check = ScriptedCheck(listOf(ForeignViolation("a.kt")))
            run.assert(definition(), treeWith(), check, update = true)

            run.assert(definition(), treeWith(), check).shouldBeEmpty()
            run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("a.kt")))).shouldBeEmpty()
        }

        "1つの assert に設定違いを並べれば、和で記録して収束する" {
            val run = TestRun()
            val a = ScriptedCheck(listOf(ForeignViolation("a.kt")))
            val b = ScriptedCheck(listOf(ForeignViolation("b.kt")))
            run.assert(definition(), treeWith(), a, b, update = true)
            val written = run.store.files[BASELINE_FILE]!!

            run.assert(definition(), treeWith(), a, b).shouldBeEmpty()
            run.assert(definition(), treeWith(), a, b, update = true)

            written shouldContain "a.kt"
            written shouldContain "b.kt"
            run.store.files[BASELINE_FILE] shouldBe written
        }

        "部分的な結果は覚えないので、次の完全な実行を止めない" {
            val run = TestRun(MemoryBaselineStore(BASELINE_FILE to ledgerOf("b.kt")))
            shouldThrow<KatachiArchitectureAssertionError> {
                run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("a.kt"))), ThrowingScriptedCheck())
            }

            run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("b.kt")))).shouldBeEmpty()
        }

        "別の台帳ファイルなら同じ check クラスでも別々に覚える" {
            val run = TestRun()
            val elsewhere = architectureOf {
                baseline("config/katachi-baseline.json")
                "Readme" { layout { "README.md".file() } }
            }
            run.assert(definition(), treeWith(), ScriptedCheck(listOf(ForeignViolation("a.kt"))), update = true)

            run.assert(elsewhere, treeWith(), ScriptedCheck(listOf(ForeignViolation("b.kt"))), update = true)

            run.store.files["/repo/config/katachi-baseline.json"]!! shouldContain "b.kt"
        }
    }
})
