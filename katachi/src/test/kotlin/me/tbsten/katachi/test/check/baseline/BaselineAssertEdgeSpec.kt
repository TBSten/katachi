package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.BaselineRuns
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

/** A definition that allows `README.md` and nothing else. The baseline file is not declared. */
private fun definition(path: String = "katachi-baseline.json"): Architecture = architectureOf {
    baseline(path)
    "Readme" { layout { "README.md".file() } }
}

private fun treeWith(vararg extra: String): FakeFileSystem = repositoryOf {
    "README.md"()
    for (path in extra) path()
}

private fun Architecture.run(
    tree: FakeFileSystem,
    environment: BaselineEnvironment,
    vararg checks: ArchitectureProcessor<Unit, List<Violation>>,
): List<Violation> = assertWith(tree, checks.toList(), 10, environment)

private fun Architecture.failure(
    tree: FakeFileSystem,
    environment: BaselineEnvironment,
    vararg checks: ArchitectureProcessor<Unit, List<Violation>>,
): KatachiArchitectureAssertionError = shouldThrow { run(tree, environment, *checks) }

/** A violation whose kind and severity a spec chooses, to reach the branches read off those two. */
private class KindedViolation(
    override val path: String,
    override val kind: ViolationKind,
    override val severity: Severity,
) : Violation {
    override val label: String get() = "Kinded"
}

/** An environment whose two system properties are given as raw strings. */
private fun rawEnvironment(
    store: MemoryBaselineStore,
    update: String? = null,
    prune: String? = null,
    standardError: MutableList<String>,
): BaselineEnvironment = BaselineEnvironment(
    store = store,
    systemProperty = { key ->
        when (key) {
            "katachi.baseline.update" -> update
            "katachi.baseline.prune" -> prune
            else -> null
        }
    },
    environmentVariable = { null },
    standardError = { standardError += it },
    runs = BaselineRuns(),
)

class BaselineAssertEdgeSpec : FreeSpec({
    "台帳ファイル自身の [UnexpectedFile]" - {
        "宣言しないまま update を2回続けても台帳は増えず、台帳ファイルの [UnexpectedFile] は報告され続ける" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))
            val first = store.files[BASELINE_FILE]!!

            // The file now exists in the project, as it would once written to disk.
            val second = definition().failure(treeWith("notes.md", "katachi-baseline.json"), environmentOf(store, update = true))

            store.files[BASELINE_FILE] shouldBe first
            first shouldNotContain "katachi-baseline.json"
            second.violations.map { "[${it.label}] ${it.path}" } shouldContainExactly listOf("[UnexpectedFile] katachi-baseline.json")
        }

        "prune でも台帳ファイルの [UnexpectedFile] は記録されず、報告される" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))
            val first = store.files[BASELINE_FILE]!!

            val failure = definition().failure(treeWith("notes.md", "katachi-baseline.json"), environmentOf(store, prune = true))

            store.files[BASELINE_FILE] shouldBe first
            failure.violations.map { it.path } shouldContain "katachi-baseline.json"
        }

        "手で足した台帳ファイル自身の項目は棚上げに使われず、stale になる" {
            val store = MemoryBaselineStore(
                BASELINE_FILE to """
                    {"version": 1, "checks": {"$LAYOUT": [{"rule": "UnexpectedFile", "path": "katachi-baseline.json"}]}}
                """.trimIndent(),
            )

            val failure = definition().failure(treeWith("katachi-baseline.json"), environmentOf(store))

            failure.violations.map { "[${it.label}] ${it.path}" } shouldContainExactly listOf(
                "[UnexpectedFile] katachi-baseline.json",
                "[StaleBaselineEntry] katachi-baseline.json",
            )
        }

        "報告の役割名は KDoc の例と同じ Baseline で、警告も同じ書き方をする" {
            val store = MemoryBaselineStore()
            val err = mutableListOf<String>()
            definition().run(treeWith(), environmentOf(store, update = true, standardError = err))

            val message = definition().failure(treeWith("katachi-baseline.json"), environmentOf(store)).message!!

            message shouldContain "\"BaselineFile\" {"
            message shouldContain "\"katachi-baseline.json\".file()"
            err.joinToString("\n") shouldContain "\"BaselineFile\" { layout { \"katachi-baseline.json\".file() } }"
        }

        "台帳ファイルを先に宣言してから初回の update をしても、まだ無い台帳ファイルの [MissingFile] を記録せず、次の実行は緑" {
            val declared = architectureOf {
                baseline()
                "Readme" { layout { "README.md".file() } }
                "BaselineFile" { layout { "katachi-baseline.json".file() } }
            }
            val store = MemoryBaselineStore()

            declared.run(treeWith("notes.md"), environmentOf(store, update = true))

            store.files[BASELINE_FILE]!! shouldNotContain "MissingFile"
            declared.run(treeWith("notes.md", "katachi-baseline.json"), environmentOf(store)).map { it.label } shouldBe emptyList()
        }

        "下の階層に置いた台帳の警告は、報告と同じく階層を / で区切った書き方をする" {
            val err = mutableListOf<String>()
            definition("config/katachi-baseline.json")
                .run(treeWith(), environmentOf(MemoryBaselineStore(), update = true, standardError = err))

            err.joinToString("\n") shouldContain "\"config\" / \"katachi-baseline.json\".file()"
        }
    }

    "部分的な結果" - {
        "普段の実行で Failed があると、stale を出さない" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))

            val failure = definition().failure(treeWith(), environmentOf(store), ThrowingScriptedCheck())

            failure.violations.map { it.label } shouldContainExactly listOf("UncheckedCheck")
        }

        "prune の実行に Failed があると、ファイルに書かずに失敗する" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))
            val before = store.files[BASELINE_FILE]
            val writes = store.writes

            val failure = definition().failure(treeWith(), environmentOf(store, prune = true), ThrowingScriptedCheck())

            store.writes shouldBe writes
            store.files[BASELINE_FILE] shouldBe before
            failure.message!! shouldContain "was not changed"
        }

        "Failed でも Warning なら部分的な結果とは扱わず、update は書く" {
            val store = MemoryBaselineStore()
            val check = ScriptedCheck(listOf(KindedViolation("a.kt", ViolationKind.Failed, Severity.Warning)))

            definition().run(treeWith("notes.md"), environmentOf(store, update = true), check)

            store.files[BASELINE_FILE]!! shouldContain "notes.md"
        }
    }

    "update と prune" - {
        "両方指定すると update になり、新しい違反も記録する" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))

            definition().run(treeWith("notes.md", "new.md"), environmentOf(store, update = true, prune = true))

            store.files[BASELINE_FILE]!! shouldContain "new.md"
        }

        "true でも false でもない値は警告し、指定しなかったものとして扱う" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))
            val before = store.files[BASELINE_FILE]
            val err = mutableListOf<String>()

            definition().failure(treeWith("notes.md", "new.md"), rawEnvironment(store, update = "yes", prune = "", standardError = err))

            store.files[BASELINE_FILE] shouldBe before
            val printed = err.joinToString("\n")
            printed shouldContain "katachi.baseline.update=yes"
            printed shouldContain "katachi.baseline.prune="
        }

        "false は警告しない" {
            val store = MemoryBaselineStore()
            definition().run(treeWith("notes.md"), environmentOf(store, update = true))
            val err = mutableListOf<String>()

            definition().run(treeWith("notes.md"), rawEnvironment(store, update = "false", prune = "FALSE", standardError = err))

            err.joinToString("\n") shouldNotContain "Warning"
        }
    }

    "台帳を持たない定義" - {
        "baseline が無ければ台帳を読まない" {
            val store = MemoryBaselineStore()
            architectureOf { "Readme" { layout { "README.md".file() } } }
                .failure(treeWith("notes.md"), environmentOf(store, update = true))
            store.files[BASELINE_FILE].shouldBeNull()
            store.writes shouldBe 0
        }
    }

    "Warning は台帳に入らない" {
        val store = MemoryBaselineStore()
        val check = ScriptedCheck(listOf(ForeignViolation("w.kt", severity = Severity.Warning)))

        definition().run(treeWith(), environmentOf(store, update = true), check)

        store.files[BASELINE_FILE]!! shouldNotContain "w.kt"
    }
})
