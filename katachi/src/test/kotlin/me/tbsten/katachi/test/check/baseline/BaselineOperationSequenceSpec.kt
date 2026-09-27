package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.KatachiBaselineNotFoundException
import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineKey
import me.tbsten.katachi.check.internal.BaselineLedger
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.check.internal.renderBaseline
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.baselineFile
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf
import kotlin.random.Random

/**
 * Random sequences of what happens to a project over time -- violations added, fixed and moved,
 * interleaved with update, prune and ordinary runs -- checked against a model a reader can hold
 * in their head: the ledger is a map from key to count, and a run compares it with the multiset
 * of violations the project has right now.
 */
private sealed interface Operation {
    data class Add(val rule: String, val path: String) : Operation
    data class Fix(val index: Int) : Operation
    data class Move(val index: Int, val to: String) : Operation
    object Update : Operation { override fun toString() = "Update" }
    object Prune : Operation { override fun toString() = "Prune" }
    object Normal : Operation { override fun toString() = "Normal" }
}

private val rules = listOf("TodoRule", "FixmeRule")
private val paths = listOf("a.kt", "b.kt", "c/d.kt", "e.kt")

private fun randomOperations(random: Random, length: Int): List<Operation> = List(length) {
    when (random.nextInt(8)) {
        0, 1 -> Operation.Add(rules.random(random), paths.random(random))
        2 -> Operation.Fix(random.nextInt(100))
        3 -> Operation.Move(random.nextInt(100), paths.random(random))
        4 -> Operation.Update
        5 -> Operation.Prune
        else -> Operation.Normal
    }
}

private data class Found(val rule: String, val path: String)

/** What a run should end in, according to the model. */
private sealed interface Outcome {
    object Green : Outcome { override fun toString() = "Green" }
    object NotFound : Outcome { override fun toString() = "NotFound" }
    data class Failed(val reported: List<String>) : Outcome
}

private val definition: Architecture = architectureOf { baseline = baselineFile() }

private fun keyOf(found: Found) = BaselineKey(check = SCRIPTED, rule = found.rule, path = found.path)

private fun counts(current: List<Found>): Map<BaselineKey, Int> = current.groupingBy(::keyOf).eachCount()

/** The model's verdict for an ordinary run of [current] against [ledger]. */
private fun expectedRun(ledger: Map<BaselineKey, Int>, current: List<Found>): Outcome {
    val found = counts(current)
    val reported = buildList {
        for (item in current) if ((found.getValue(keyOf(item))) > (ledger[keyOf(item)] ?: 0)) add("[${item.rule}] ${item.path}")
        for ((key, allowed) in ledger) {
            val count = found[key] ?: 0
            if (count < allowed) add("[StaleBaselineEntry] ${key.path} ${key.rule} $allowed $count")
        }
    }
    return if (reported.isEmpty()) Outcome.Green else Outcome.Failed(reported.sorted())
}

private fun describe(violations: List<Violation>): List<String> = violations.map {
    if (it is StaleBaselineEntry) "[StaleBaselineEntry] ${it.path} ${it.rule} ${it.allowed} ${it.found}" else "[${it.label}] ${it.path}"
}.sorted()

class BaselineOperationSequenceSpec : FreeSpec({
    "操作の列をランダムに並べても、キー → 件数のモデルと結果が一致する" {
        val tree = repositoryOf { }
        repeat(300) { seed ->
            val random = Random(seed)
            val operations = randomOperations(random, 12)
            val store = MemoryBaselineStore()
            val check = ScriptedCheck()
            val current = mutableListOf<Found>()
            var ledger: Map<BaselineKey, Int>? = null

            fun actual(update: Boolean = false, prune: Boolean = false): Outcome {
                check.violations = current.map { ForeignViolation(it.path, label = it.rule) }
                return try {
                    definition.assertWith(tree, listOf(check), 10, environmentOf(store, update = update, prune = prune))
                    Outcome.Green
                } catch (failure: KatachiArchitectureAssertionError) {
                    Outcome.Failed(describe(failure.violations))
                } catch (_: KatachiBaselineNotFoundException) {
                    Outcome.NotFound
                }
            }

            for ((step, operation) in operations.withIndex()) {
                withClue("seed=$seed step=$step operations=$operations current=$current ledger=$ledger") {
                    when (operation) {
                        is Operation.Add -> current += Found(operation.rule, operation.path)
                        is Operation.Fix -> if (current.isNotEmpty()) current.removeAt(operation.index % current.size)
                        is Operation.Move -> if (current.isNotEmpty()) {
                            val index = operation.index % current.size
                            current[index] = current[index].copy(path = operation.to)
                        }
                        Operation.Update -> {
                            actual(update = true) shouldBe Outcome.Green
                            ledger = counts(current)
                            store.files[BASELINE_FILE] shouldBe renderBaseline(BaselineLedger(ledger!!))
                            // Right after an update, an ordinary run is green, and updating again
                            // changes nothing.
                            val written = store.files[BASELINE_FILE]
                            actual() shouldBe Outcome.Green
                            actual(update = true) shouldBe Outcome.Green
                            store.files[BASELINE_FILE] shouldBe written
                        }
                        Operation.Prune -> {
                            val known = ledger
                            if (known == null) {
                                actual(prune = true) shouldBe Outcome.NotFound
                            } else {
                                val found = counts(current)
                                val pruned = known.mapValues { (key, allowed) -> minOf(allowed, found[key] ?: 0) }
                                    .filterValues { it > 0 }
                                actual(prune = true) shouldBe expectedRun(pruned, current)
                                ledger = pruned
                                store.files[BASELINE_FILE] shouldBe renderBaseline(BaselineLedger(pruned))
                            }
                        }
                        Operation.Normal -> {
                            val known = ledger
                            actual() shouldBe (if (known == null) Outcome.NotFound else expectedRun(known, current))
                        }
                    }
                }
            }
        }
    }
})
