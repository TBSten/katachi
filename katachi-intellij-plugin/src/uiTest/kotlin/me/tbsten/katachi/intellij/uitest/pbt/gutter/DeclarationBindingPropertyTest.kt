package me.tbsten.katachi.intellij.uitest.pbt.gutter

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DeclarationModel
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.presentation.gutter.BoundExplanation
import me.tbsten.katachi.intellij.presentation.gutter.DeclarationBinding
import me.tbsten.katachi.intellij.presentation.gutter.SourceDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [DeclarationBinding] (design 2.4) over random files and random sequences of edits made after katachi explained the
 * file: blank lines, new and removed declarations, renames, changed kinds, new overloads. The binding goes by kind and
 * name, so what the edits keep must stay bound to the same explanation, and what they make new must say "not computed".
 * `-Pkatachi.pbt.seed=N` tries another seed, `-Pkatachi.pbt.scale=N` runs N times as many.
 */
class DeclarationBindingPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261005L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    // covers: 一意な名前
    @Test
    fun `名前が一意なら 両側の行がどれだけ違っても その説明に結ばれる`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { fileSeed ->
            val random = Random(fileSeed)
            val file = BindingFiles.random(random, overloads = false)
            val explanations = file.explanations()
            val scattered = file.declarations.map { it.copy(line = random.nextInt(1, 2_000)) }
            val bound = DeclarationBinding.bind(scattered, explanations)
            bound.forEachIndexed { index, it -> assertSame("seed=$fileSeed #$index", explanations[index], it.explanation.model) }
        }
    }

    // covers: オーバーロード
    @Test
    fun `オーバーロードは 説明を書いたときと同じ行なら それぞれ自分の行の説明に結ばれる`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { fileSeed ->
            val file = BindingFiles.random(Random(fileSeed), overloads = true)
            val explanations = file.explanations()
            DeclarationBinding.bind(file.declarations, explanations).forEachIndexed { index, it ->
                assertSame("seed=$fileSeed #$index ${file.declarations}", explanations[index], it.explanation.model)
            }
        }
    }

    // covers: オーバーロード, 行のずれ
    @Test
    fun `オーバーロードを消したら 残った宣言は行の最も近い説明に結ばれ 同じ差なら前の行`() {
        val explanations = BindingFile(listOf(fn(3), fn(9))).explanations()
        val bound = DeclarationBinding.bind(listOf(fn(5)), explanations)
        assertEquals(listOf(explanations[0]), bound.map { it.explanation.model })
        val tie = DeclarationBinding.bind(listOf(fn(6)), explanations)
        assertSame(explanations[0], tie.single().explanation.model)
    }

    // covers: オーバーロード, 行のずれ
    @Test
    fun `オーバーロードを足したら 説明は行の最も近い宣言に1つずつ結ばれ 足した宣言はまだ計算していない`() {
        val explanations = BindingFile(listOf(fn(3), fn(9))).explanations()
        val (first, second) = explanations.map(BoundExplanation::Explained)
        val middle = DeclarationBinding.bind(listOf(fn(3), fn(5), fn(9)), explanations)
        assertEquals(listOf(first, BoundExplanation.NotComputed, second), middle.map { it.explanation })
        val last = DeclarationBinding.bind(listOf(fn(3), fn(9), fn(12)), explanations)
        assertEquals(listOf(first, second, BoundExplanation.NotComputed), last.map { it.explanation })
        // One explanation, the name overloaded afterwards: only the nearest declaration borrows it.
        val single = BindingFile(listOf(fn(10))).explanations()
        val added = DeclarationBinding.bind(listOf(fn(4), fn(11)), single)
        assertEquals(listOf(BoundExplanation.NotComputed, BoundExplanation.Explained(single[0])), added.map { it.explanation })
    }

    // covers: 行のずれ
    @Test
    fun `宣言の上に空行を足しても 行をどうずらしても 並びが変わらなければ結び付きは変わらない`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { fileSeed ->
            val random = Random(fileSeed)
            val file = BindingFiles.random(random, overloads = true)
            val explanations = file.explanations()
            val before = DeclarationBinding.bind(file.declarations, explanations).map { it.explanation }
            var shift = 0
            val shifted = file.declarations.map { declaration ->
                shift += random.nextInt(0, 30)
                declaration.copy(line = declaration.line + shift)
            }
            assertEquals("seed=$fileSeed", before, DeclarationBinding.bind(shifted, explanations).map { it.explanation })
        }
    }

    // covers: 改名, 種類の変更
    @Test
    fun `改名した宣言と種類を変えた宣言は まだ計算していない`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { fileSeed ->
            val random = Random(fileSeed)
            val file = BindingFiles.random(random, overloads = true)
            val target = random.nextInt(file.declarations.size)
            val original = file.declarations[target]
            val renamed = original.copy(name = original.name + "Renamed")
            val rekinded = original.copy(kind = SourceDeclarationKind.known.first { it != original.kind && it != SourceDeclarationKind.Fun })
            for (changed in listOf(renamed, rekinded)) {
                val edited = file.declarations.toMutableList().also { it[target] = changed }
                val bound = DeclarationBinding.bind(edited, file.explanations())
                val clashes = file.declarations.any { it.kind == changed.kind && it.name == changed.name }
                if (!clashes) assertEquals("seed=$fileSeed $changed", BoundExplanation.NotComputed, bound[target].explanation)
            }
        }
    }

    // covers: 説明の順
    @Test
    fun `説明の順を入れ替えても 結び付きは同じ`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { fileSeed ->
            val random = Random(fileSeed)
            val file = BindingFiles.random(random, overloads = true)
            val explanations = file.explanations()
            val expected = DeclarationBinding.bind(file.declarations, explanations)
            assertEquals("seed=$fileSeed", expected, DeclarationBinding.bind(file.declarations, explanations.shuffled(random)))
        }
    }

    // covers: 操作の列
    @Test
    fun `説明の後に編集の列を流しても 残った宣言は元の説明に 新しい宣言はまだ計算していないに結ばれる`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { fileSeed ->
            val random = Random(fileSeed)
            val start = BindingFiles.random(random, overloads = true)
            val explanations = start.explanations()
            var file = start.tracked()
            val log = mutableListOf<String>()
            repeat(random.nextInt(1, 12)) {
                val edit = BindingEdits.random(random, file)
                log += edit.name
                file = edit.apply(file)
                BindingInvariants.check(file, explanations) { "seed=$fileSeed after ${log.joinToString(" → ")}" }
            }
        }
    }

    private fun fn(line: Int) = SourceDeclaration(SourceDeclarationKind.Fun, "load", line)
}

/** Assertions about one binding of an edited file against the explanations of the file before the edits. */
internal object BindingInvariants {
    fun check(file: TrackedFile, explanations: List<DeclarationModel>, where: () -> String) {
        val sources = file.entries.map { it.declaration }
        val bound = DeclarationBinding.bind(sources, explanations)
        val originalKeys = explanations.map { it.kind to it.name }.toSet()
        for ((index, binding) in bound.withIndex()) {
            val source = sources[index]
            when (val explanation = binding.explanation) {
                is BoundExplanation.Explained -> {
                    assertEquals("${where()}: #$index binds its own kind and name", source.kind to source.name, explanation.declaration.kind to explanation.declaration.name)
                }
                // Not computed: a name nobody explained, or one more declaration of that name than katachi saw.
                BoundExplanation.NotComputed -> assertTrue(
                    "${where()}: #$index $source was explained",
                    (source.kind to source.name) !in originalKeys ||
                        sources.count { it.kind == source.kind && it.name == source.name } > explanations.count { it.kind == source.kind && it.name == source.name },
                )
            }
        }
        // A kind and name whose declarations are all the original ones, as many as katachi saw: each keeps its own explanation.
        for ((key, indices) in sources.indices.groupBy { sources[it].kind to sources[it].name }) {
            val origins = indices.map { file.entries[it].origin }
            val originalCount = explanations.count { (it.kind to it.name) == key }
            if (origins.any { it == null } || origins.size != originalCount) continue
            for ((position, index) in indices.withIndex()) {
                val origin = checkNotNull(origins[position])
                assertSame("${where()}: #$index kept its explanation", explanations[origin], bound[index].explanation.model)
            }
        }
    }
}
