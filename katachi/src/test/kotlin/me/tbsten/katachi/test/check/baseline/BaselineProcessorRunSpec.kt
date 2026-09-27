package me.tbsten.katachi.test.check.baseline

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.LayoutCheck
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.baselineFile
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.internal.runProcessors
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf

private val definition: Architecture = architectureOf {
    baseline = baselineFile()
    "Readme" { layout { "README.md".file() } }
}

private fun ledgerHolding(vararg paths: String): MemoryBaselineStore = MemoryBaselineStore(
    BASELINE_FILE to """
        {"version": 1, "checks": {"$LAYOUT": [${paths.joinToString(", ") { """{"rule": "UnexpectedFile", "path": "$it"}""" }}]}}
    """.trimIndent(),
)

private fun runLayout(store: MemoryBaselineStore, vararg extra: String): Pair<Int, String> {
    val lines = mutableListOf<String>()
    val summary = runProcessors(
        architecture = definition,
        registry = mapOf("layout" to LayoutCheck::class.java),
        processorKeys = listOf("layout"),
        rawArgs = emptyMap(),
        fileSystem = repositoryOf { "README.md"(); for (path in extra) path() },
        out = lines::add,
        baselineEnvironment = environmentOf(store),
    )
    return summary.failed to lines.joinToString("\n")
}

/** A processor that is not a check: it answers with names, and this time with none. */
internal class NoNamesProcessor : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> = Result.success(emptyList())
}

class BaselineProcessorRunSpec : FreeSpec({
    "processor 実行でも台帳と突き合わせる" - {
        "棚上げした違反だけなら [OK] になる" {
            val (failed, output) = runLayout(ledgerHolding("notes.md"), "notes.md")

            failed shouldBe 0
            output shouldContain "[OK] layout"
            output shouldContain "Baseline $BASELINE_URI held back 1 violation."
        }

        "新しい違反があれば [FAILED] になり、棚上げした違反は出ない" {
            val (failed, output) = runLayout(ledgerHolding("notes.md"), "notes.md", "new.md")

            failed shouldBe 1
            output shouldContain "[FAILED] layout"
            output shouldContain "[UnexpectedFile] file:///repo/new.md"
            output shouldNotContain "file:///repo/notes.md"
        }

        "解消済みの項目は stale として [FAILED] になる" {
            val (failed, output) = runLayout(ledgerHolding("notes.md"))

            failed shouldBe 1
            output shouldContain "[StaleBaselineEntry] file:///repo/notes.md"
        }

        "台帳が無ければ [FAILED] になり、作るコマンドを示す" {
            val (failed, output) = runLayout(MemoryBaselineStore(), "notes.md")

            failed shouldBe 1
            output shouldContain "-Dkatachi.baseline.update=true"
        }
    }

    "check でない processor が空のリストを返しても、台帳を読まない" {
        val lines = mutableListOf<String>()
        val store = MemoryBaselineStore()
        val summary = runProcessors(
            architecture = definition,
            registry = mapOf("names" to NoNamesProcessor::class.java),
            processorKeys = listOf("names"),
            rawArgs = emptyMap(),
            fileSystem = repositoryOf { "README.md"() },
            out = lines::add,
            baselineEnvironment = environmentOf(store),
        )

        summary.failed shouldBe 0
        lines.joinToString("\n") shouldNotContain "Baseline"
    }
})
