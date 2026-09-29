package me.tbsten.katachi.intellij.uitest.dialog

import io.kotest.property.PropTest
import io.kotest.property.toPropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.take
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.CapturePlace
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.uitest.pbt.DialogWorld
import me.tbsten.katachi.intellij.uitest.pbt.dialogScenarioArb
import me.tbsten.katachi.intellij.uitest.pbt.realWorldArb
import me.tbsten.katachi.intellij.uitest.pbt.syntheticWorldArb
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sequences of operations on the real generate dialog ViewModel, tied to the real placement index and
 * New menu tree, with the state checked after every step (see [DialogMachine]). Definitions come from
 * a generator built from structure and from the real JSON of the three samples.
 *
 * `-Pkatachi.pbt.seed=N` tries another seed, `-Pkatachi.pbt.scale=N` runs N times as many sequences.
 * A failure prints the shrunk world, origin and operations, and the steps that led to it.
 */
class DialogPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20260929L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0

    private fun config(defaultIterations: Int) = PropTest(
        seed = seed,
        iterations = (defaultIterations * scale).toInt().coerceAtLeast(1),
        shrinkingMode = ShrinkingMode.Bounded(400),
    ).toPropTestConfig()

    private val reached = sortedMapOf<String, Int>()

    private fun DialogMachine.collect() = this.reached.forEach { (key, count) -> this@DialogPropertyTest.reached.merge(key, count, Int::plus) }

    // covers: 論点1, 論点2, 論点6, 論点7
    @Test
    fun `どんな操作の列でも見本パス・生成の可否・候補・予告・引き継ぎが仕様どおりで、起点の見本パスは起点と一致する`() = runBlocking {
        checkAll(config(defaultIterations = 250), dialogScenarioArb(syntheticWorldArb, 1..30)) { scenario ->
            DialogMachine(scenario).use { machine ->
                machine.run()
                machine.collect()
            }
        }
        println("[pbt] dialog synthetic: reached=$reached (seed=$seed)")
        // The scenarios must have gone through the rare transitions, or the checks above prove little.
        for (key in listOf("op SelectTemplate", "op SelectDefinition", "op Type", "op Shrink", "op ExternalWrite", "op Cancel", "template replaced", "no candidates", "two definitions", "one candidate", "notice WillCreate", "notice CannotOverwrite", "notice WillOverwriteEmpty", "notice DecidedByKatachi", "collapsed", "generate requested", "generate refused", "captures only", "many fields")) {
            assertTrue("never reached '$key': $reached", (reached[key] ?: 0) > 0)
        }
    }

    // covers: 論点1, 論点2, 論点6, 論点7
    @Test
    fun `sample 3種の本物のJSONの上で操作しても同じ不変条件が成り立つ`() = runBlocking {
        checkAll(config(defaultIterations = 80), dialogScenarioArb(realWorldArb, 1..30)) { scenario ->
            DialogMachine(scenario).use { machine ->
                machine.run()
                machine.collect()
            }
        }
        println("[pbt] dialog real JSON: reached=$reached (seed=$seed)")
        assertTrue("never reached a definition select box: $reached", (reached["two definitions"] ?: 0) > 0)
    }

    // covers: 論点1
    @Test
    fun `生成器は入れ子・ルート直下・全部の型・分岐・モジュールの capture・部分 capture・パラメータ0個と多数・定義1つと2つ・使えないテンプレートを出す`() {
        val worlds = Arb.syntheticWorlds().take(300, RandomSource.seeded(seed)).toList()
        val templates = worlds.flatMap { world -> world.definitions.flatMap { it.pool } }
        val details = templates.mapNotNull { it.detail }
        val kinds = details.flatMap { it.parameters }.map { it::class.simpleName }.toSet()
        val patterns = details.map { it.files.first().pattern }
        val facts = mapOf(
            "nested group" to templates.any { it.summary.roleName.count { c -> c == '.' } >= 2 },
            "role at the root" to templates.any { '.' !in it.summary.roleName },
            "every parameter type" to setOf("StringParam", "BooleanParam", "IntParam", "EnumParam").all { it in kinds },
            "a branch" to details.any { it.branches.isNotEmpty() },
            "a branch that adds" to details.any { d -> d.branches.any { it.addedParameters.isNotEmpty() } },
            "a branch that removes" to details.any { d -> d.branches.any { it.removedParameters.isNotEmpty() } },
            "a module capture" to details.any { d -> d.captures.any { c -> c.places.any { it.kindName == CapturePlace.KIND_MODULE } } },
            "a partial capture" to patterns.any { p -> p.split('/').any { s -> s.contains("\${") && s.length > s.indexOf('}') + 1 && s != "\${${s.substringAfter("\${").substringBefore('}')}}" } },
            "a derived part" to patterns.any { it.contains('<') },
            "no parameter" to details.any { it.parameters.isEmpty() },
            "many parameters" to details.any { it.parameters.size >= 5 },
            "no capture" to details.any { it.captures.isEmpty() },
            "several captures" to details.any { it.captures.size >= 2 },
            "one definition" to worlds.any { it.definitions.size == 1 },
            "two definitions" to worlds.any { it.definitions.size == 2 },
            "one template in a definition" to worlds.any { w -> w.definitions.any { it.pool.size == 1 } },
            "two templates of one role" to templates.groupBy { it.summary.roleName }.any { it.value.size >= 2 },
            "a preview that failed" to templates.any { it.detail == null },
            "an unknown parameter kind" to details.any { d -> d.parameters.any { it is ParameterModel.UnknownParam } },
        )
        val missing = facts.filterValues { !it }.keys
        assertTrue("the generator never made: $missing", missing.isEmpty())
    }

    private fun Arb.Companion.syntheticWorlds(): Arb<DialogWorld> = syntheticWorldArb
}
