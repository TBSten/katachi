package me.tbsten.katachi.intellij.uitest.pbt.placement

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.toPropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.shuffle
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.snapshotOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * The placement index over generated patterns (fixed, captured, partly captured segments, module
 * captures and what is derived from them) and generated values. The four properties of the index
 * layer; the New menu's tree and the dialog's sample path are checked against the index by D3a.
 */
class PlacementIndexPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20260929L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (200 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    /** One pattern with its values, in [definitions] definitions of their own roots, each holding [templates]. */
    private data class Scenario(val spec: PatternSpec, val filling: Filling, val definitions: Int) {
        val segments: List<String> = spec.segs.map { filling.segmentText(it, spec.moduleNames) }
        fun index(templates: (Int) -> List<TemplateModel> = { listOf(spec.template(TARGET)) }): TemplatePlacementIndex =
            TemplatePlacementIndex.build((0 until definitions).map { snapshotOf(moduleOf(it), templates(it)) }) { it.linkedRootPath }

        fun path(definition: Int, segments: List<String>): Path = rootOf(definition).resolveSegments(segments)
        override fun toString(): String = "$spec values=${filling.values} pick=${filling.pick} definitions=$definitions -> ${segments.joinToString("/")}"
    }

    private val scenarioArb: Arb<Scenario> = arbitrary {
        val spec = patternArb.bind()
        Scenario(spec, fillingArb(spec).bind(), Arb.int(1..2).bind())
    }

    private fun decidedBefore(spec: PatternSpec, k: Int, values: Map<String, String>): Map<String, String> =
        spec.segs.take(k).flatMap { it.caps }.associate { it.name to values.getValue(it.name) }

    private fun expectedFile(spec: PatternSpec, values: Map<String, String>) = decidedBefore(spec, spec.segs.size, values)

    private fun PlacementMatch.isTarget() = template.template.template.startsWith("t.")

    private fun PlacementMatch.summary() = listOf(definition.gradlePath, template.template.template, decided, undecided, remainingPath, targetUndecided)

    // covers: 論点1
    @Test
    fun `パターンに値を当てはめたパスを逆引きすると同じ値に戻る`() = runBlocking {
        checkAll(config, scenarioArb) { sc ->
            val index = sc.index()
            for (definition in 0 until sc.definitions) {
                val matches = index.matchesForFile(sc.path(definition, sc.segments))
                assertEquals("$sc", listOf(":d$definition" to TARGET), matches.map { it.definition.gradlePath to it.template.template.template })
                val match = matches.single()
                assertEquals("$sc", expectedFile(sc.spec, sc.filling.values), match.decided)
                assertEquals("$sc", emptyList<String>(), match.undecided)
                assertEquals("$sc", "", match.remainingPath)
                assertEquals("$sc", false, match.targetUndecided)
            }
        }
        Unit
    }

    // covers: 論点1
    @Test
    fun `ディレクトリに当たるのは当てはめ直すと元のディレクトリになるときでその値が決まる`() = runBlocking {
        checkAll(config, scenarioArb, Arb.int(0..1000)) { sc, salt ->
            val index = sc.index()
            val spec = sc.spec
            val n = spec.segs.size
            for (k in 0 until n) {
                val directory = sc.path(0, sc.segments.take(k))
                val match = index.matchesForDirectory(directory).singleOrNull { it.definition.gradlePath == ":d0" }
                assertTrue("$sc k=$k: no match", match != null)
                match!!
                val decided = decidedBefore(spec, k, sc.filling.values)
                assertEquals("$sc k=$k", decided, match.decided)
                val remaining = spec.segs.drop(k)
                assertEquals("$sc k=$k", remaining.flatMap { it.caps }.map { it.name }.filter { it !in decided }.distinct(), match.undecided)
                assertEquals("$sc k=$k", remaining.joinToString("/") { it.shown }, match.remainingPath)
                assertEquals("$sc k=$k", remaining.any { it.hasDerived }, match.targetUndecided)
                // Filling the pattern's first k segments again with what was decided gives the directory.
                val refilled = Filling(sc.filling.values + decided, sc.filling.pick)
                assertEquals("$sc k=$k", sc.segments.take(k), spec.segs.take(k).map { refilled.segmentText(it, spec.moduleNames) })
            }
            // Another value in a segment that is a capture alone (not the module's): the directory still fits, with that value.
            val alone = spec.segs.withIndex().filter { (_, seg) -> seg.parts.size == 1 && seg.caps.singleOrNull()?.module == false }
            if (alone.isNotEmpty() && n > 1) {
                val (i, seg) = alone[salt % alone.size]
                if (i < n) {
                    val other = "Other$salt"
                    val changed = sc.segments.take(i + 1).toMutableList().also { it[i] = other }
                    val match = index.matchesForDirectory(sc.path(0, changed)).singleOrNull { it.definition.gradlePath == ":d0" }
                    assertTrue("$sc: $other at $i", match != null)
                    assertEquals("$sc: $other at $i", other, match!!.decided[seg.caps.single().name])
                }
            }
        }
        Unit
    }

    // covers: 論点1
    @Test
    fun `固定のセグメントと食い違うパスやファイルより深いパスや使えないテンプレートは出さない`() = runBlocking {
        checkAll(config, scenarioArb, Arb.int(0..1000)) { sc, salt ->
            val spec = sc.spec
            val n = spec.segs.size
            val index = sc.index { listOf(spec.template(TARGET), failedPreview(spec, "x.Failed"), unknownKind(spec, "x.Unknown")) }
            // The unusable ones never show, whatever the path.
            fun roles(matches: List<PlacementMatch>) = matches.map { it.template.template.template }.toSet()
            assertEquals("$sc", setOf(TARGET), roles(index.matchesForFile(sc.path(0, sc.segments))))
            for (k in 0 until n) assertEquals("$sc k=$k", setOf(TARGET), roles(index.matchesForDirectory(sc.path(0, sc.segments.take(k)))))

            val single = sc.index()
            // The file itself as a directory, and anything below the file: nothing.
            assertEquals("$sc", emptyList<PlacementMatch>(), single.matchesForDirectory(sc.path(0, sc.segments)))
            assertEquals("$sc", emptyList<PlacementMatch>(), single.matchesForDirectory(sc.path(0, sc.segments + "deeper")))
            assertEquals("$sc", emptyList<PlacementMatch>(), single.matchesForFile(sc.path(0, sc.segments + "deeper.kt")))
            assertEquals("$sc", emptyList<PlacementMatch>(), single.matchesForFile(sc.path(0, sc.segments.dropLast(1))))
            // Outside every root.
            assertEquals("$sc", emptyList<PlacementMatch>(), single.matchesForFile(ROOT.resolve("elsewhere").resolveSegments(sc.segments)))

            // One character of a fixed part changed: no directory at or below that segment, and not the file.
            val withLiteral = spec.segs.withIndex().filter { (_, seg) -> seg.hasLiteral }
            val (i, seg) = withLiteral[salt % withLiteral.size]
            val literal = seg.parts.filterIsInstance<Part.Lit>().let { it[(salt / 7) % it.size] }
            val at = (salt / 13) % literal.text.length
            val mutatedLiteral = literal.text.replaceRange(at, at + 1, "#")
            val mutatedSegment = sc.segments[i].replaceFirst(literal.text, mutatedLiteral)
            val mutated = sc.segments.toMutableList().also { it[i] = mutatedSegment }
            assertEquals("$sc: $mutatedSegment at $i", emptyList<PlacementMatch>(), single.matchesForFile(sc.path(0, mutated)))
            for (k in 0 until n) {
                val found = single.matchesForDirectory(sc.path(0, mutated.take(k)))
                if (k > i) assertEquals("$sc: $mutatedSegment at $i, k=$k", emptyList<PlacementMatch>(), found)
                else assertEquals("$sc k=$k", 1, found.count { it.definition.gradlePath == ":d0" })
            }
        }
        Unit
    }

    // covers: 論点1
    @Test
    fun `関係のないテンプレートを混ぜても並びを入れ替えても当たりの中身は変わらず並びはJSONの並び`() = runBlocking {
        checkAll(config, scenarioArb, Arb.shuffle(listOf("t.T0", "t.T1", "t.T2")), Arb.int(0..1000)) { sc, shuffled, salt ->
            val spec = sc.spec
            val noise = listOf(
                failedPreview(spec, "n.Failed"),
                unknownKind(spec, "n.Unknown"),
                PatternSpec(listOf(Seg(listOf(Part.Lit("zz-noise"))), Seg(listOf(Part.Cap("q"), Part.Lit(".kt"))))).template("n.Other"),
                PatternSpec(listOf(Seg(listOf(Part.Lit("elsewhere"))), Seg(listOf(Part.Lit("Foo.kt"))))).template("n.Fixed"),
            )
            val order = shuffled.take(2 + salt % 2)
            val plain = order.map { spec.template(it) }
            val mixed = mixIn(plain, noise, salt)

            val base = sc.index { plain }
            val withNoise = sc.index { mixed }
            val second = sc.index { i -> if (i == 0) mixed else noise } // a definition of nothing but noise
            val paths = (0..spec.segs.size).map { k -> k to sc.path(0, sc.segments.take(k)) }
            fun view(index: TemplatePlacementIndex): List<List<Any?>> =
                paths.map { (k, path) -> if (k == spec.segs.size) index.matchesForFile(path) else index.matchesForDirectory(path) }
                    .flatMap { matches -> matches.filter { it.definition.gradlePath == ":d0" && it.isTarget() }.map { it.summary() } }
            assertEquals("$sc $order", view(base), view(withNoise))
            assertEquals("$sc $order", view(base), view(second))
            val expectedRoles = order
            for ((k, path) in paths) {
                val matches = (if (k == spec.segs.size) withNoise.matchesForFile(path) else withNoise.matchesForDirectory(path))
                val here = matches.filter { it.definition.gradlePath == ":d0" }
                assertEquals("$sc $order k=$k", expectedRoles, here.filter { it.isTarget() }.map { it.template.template.template })
                // Only the root fits everything; below it the unrelated templates and the unusable ones stay out.
                if (k > 0) assertEquals("$sc $order k=$k", expectedRoles, here.map { it.template.template.template })
            }
        }
        Unit
    }

    /** [noise] put among [templates] at positions [salt] decides, [templates] keeping their order. */
    private fun mixIn(templates: List<TemplateModel>, noise: List<TemplateModel>, salt: Int): List<TemplateModel> {
        val result = templates.toMutableList()
        noise.forEachIndexed { index, template -> result.add((salt / (index + 1) + index) % (result.size + 1), template) }
        return result
    }

    private companion object {
        const val TARGET = "t.Target"
    }
}
