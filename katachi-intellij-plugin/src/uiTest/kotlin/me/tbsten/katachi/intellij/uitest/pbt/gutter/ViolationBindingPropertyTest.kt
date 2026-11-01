package me.tbsten.katachi.intellij.uitest.pbt.gutter

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationTarget
import me.tbsten.katachi.intellij.presentation.gutter.RuleSite
import me.tbsten.katachi.intellij.presentation.gutter.ViolationAnnotations
import me.tbsten.katachi.intellij.presentation.gutter.ViolationBinding
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import me.tbsten.katachi.intellij.testing.englishGutter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths
import kotlin.random.Random

/**
 * [ViolationBinding] and [ViolationAnnotations] (design 2.5) over random violations: whatever the line, level, message and
 * properties of a report, the same kind of violation lands in the same kind of place and reads the same way.
 * `-Pkatachi.pbt.seed=N` tries another seed, `-Pkatachi.pbt.scale=N` runs N times as many.
 */
class ViolationBindingPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261005L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val path = Paths.get("/project/arch/src/main/kotlin/com/example/Service.kt")
    private val names = listOf("HealthService", "HealthRepository", "load", "Outer.member", "DEFAULT", "toDomain")

    private fun level(random: Random) = SarifLevel.entries[random.nextInt(SarifLevel.entries.size)]

    private fun line(random: Random): Int? = when (random.nextInt(4)) {
        0 -> null
        1 -> random.nextInt(-3, 1)
        else -> random.nextInt(1, 5_000)
    }

    private fun message(random: Random): String = List(random.nextInt(0, 4)) { "line ${random.nextInt(100)}" }.joinToString("\n")

    private fun unsatisfied(random: Random, declaration: String?, line: Int? = line(random)): ViolationModel =
        AnnotationFixtures.unsatisfied(path, declaration, line, level = level(random), message = message(random))

    // covers: 宣言の名前
    @Test
    fun `宣言を持つ違反は 行や level や メッセージがどうでも 同じ宣言の名前に付く`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            val name = names[random.nextInt(names.size)]
            val first = ViolationBinding.targetOf(unsatisfied(random, name, line = 7))
            val other = ViolationBinding.targetOf(unsatisfied(random, name))
            val target = other as? AnnotationTarget.AtDeclaration ?: throw AssertionError("seed=$caseSeed: $other")
            assertEquals("seed=$caseSeed", name, target.name)
            assertEquals("seed=$caseSeed", AnnotationTarget.AtDeclaration(name, 7), first)
        }
    }

    // covers: 宣言を消す
    @Test
    fun `宣言の無い違反は 行があれば その行 無ければ ファイルの上`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            val line = line(random)
            val target = ViolationBinding.targetOf(unsatisfied(random, declaration = null, line = line))
            val expected = if (line != null && line > 0) AnnotationTarget.AtLine(line) else AnnotationTarget.FileLevel
            assertEquals("seed=$caseSeed line=$line", expected, target)
        }
    }

    // covers: 描かないもの
    @Test
    fun `UncheckedFile は いつもファイルの上 それ以外の種類は 何が書いてあっても描かない`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            assertEquals("seed=$caseSeed", AnnotationTarget.FileLevel, ViolationBinding.targetOf(AnnotationFixtures.unchecked(path, level(random))))
            val labels = listOf("UnexpectedFile", "UnexpectedDirectory", "StaleBaselineEntry", "TodoRule", "Future")
            val label = labels[random.nextInt(labels.size)]
            val other = unsatisfied(random, names[random.nextInt(names.size)]).copy(label = label, ruleId = "katachi.$label")
            assertEquals("seed=$caseSeed $label", AnnotationTarget.Skipped, ViolationBinding.targetOf(other))
        }
    }

    // covers: 並び
    @Test
    fun `結び付けは 違反を 1 つずつ 元の順のまま 渡す`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            val violations = List(random.nextInt(0, 10)) {
                when (random.nextInt(4)) {
                    0 -> AnnotationFixtures.unchecked(path)
                    1 -> AnnotationFixtures.unexpected(path)
                    2 -> unsatisfied(random, null)
                    else -> unsatisfied(random, names[random.nextInt(names.size)])
                }
            }
            val bound = ViolationBinding.bindSourceSide(violations)
            assertEquals("seed=$caseSeed", violations, bound.map { it.violation })
            assertEquals("seed=$caseSeed", violations.map(ViolationBinding::targetOf), bound.map { it.target })
        }
    }

    // covers: 古い検査は弱くなる
    @Test
    fun `古い検査の色は 新しい検査より強くならず error は必ず 1 段弱くなる`() {
        for (level in SarifLevel.entries) {
            val fresh = ViolationAnnotations.severityOf(level, stale = false)
            val stale = ViolationAnnotations.severityOf(level, stale = true)
            assertTrue("$level: $stale is not stronger than $fresh", stale.ordinal >= fresh.ordinal)
            if (fresh == AnnotationSeverity.Error) assertEquals(AnnotationSeverity.Warning, stale)
        }
    }

    // covers: ホバー
    @Test
    fun `ホバーは stale のときだけ stale で始まり katachi か katachi の次に宣言を言い null を含まない`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            val violation = if (random.nextBoolean()) unsatisfied(random, names[random.nextInt(names.size)]) else unsatisfied(random, null)
            val reduced = if (random.nextBoolean()) violation else violation.copy(properties = violation.properties.filterKeys { random.nextBoolean() })
            val target = ViolationBinding.targetOf(reduced)
            val stale = random.nextBoolean()
            val hover = ViolationAnnotations.hoverOf(reduced, target, stale, englishGutter)
            val where = "seed=$caseSeed stale=$stale $hover"
            assertEquals(where, stale, hover.startsWith(englishGutter.stalePrefix))
            assertTrue(where, hover.removePrefix(englishGutter.stalePrefix).startsWith("katachi: "))
            assertFalse(where, hover.contains("null"))
            if (target is AnnotationTarget.AtDeclaration) assertTrue(where, hover.contains("rejected ${target.name}"))
        }
    }

    // covers: 規則の場所
    @Test
    fun `declaredAt は ファイル名と行に分けられ 規則の見出しに同じ形で戻る`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            val file = "Role${random.nextInt(50)}.kt"
            val line = random.nextInt(1, 10_000)
            val violation = AnnotationFixtures.unsatisfied(path, "A", 3, declaredAt = "$file:$line")
            val site = ViolationBinding.ruleSiteOf(violation)
            assertEquals("seed=$caseSeed", RuleSite.Declared(DeclarationSiteRef(file, line)), site)
            assertEquals("seed=$caseSeed", "$file:$line", ViolationAnnotations.ruleLabelOf(site ?: throw AssertionError("seed=$caseSeed")))
            val related = SarifLocation(Paths.get("/p/$file"), line)
            assertEquals("seed=$caseSeed", RuleSite.At(related), ViolationBinding.ruleSiteOf(violation.copy(related = listOf(related))))
        }
    }
}
