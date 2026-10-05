package me.tbsten.katachi.intellij.uitest.pbt.gutter

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.presentation.gutter.GutterFreshness
import me.tbsten.katachi.intellij.presentation.gutter.GutterInput
import me.tbsten.katachi.intellij.presentation.gutter.GutterMarks
import me.tbsten.katachi.intellij.presentation.gutter.GutterTooltips
import me.tbsten.katachi.intellij.presentation.gutter.SourceFileKind
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths
import kotlin.random.Random

/**
 * The state table's pure parts (design 2.6) over random states: the line a ◆ adds when the definition could not be read
 * again, and when a ◆ asks the definition to load. `-Pkatachi.pbt.seed=N` tries another seed, `-Pkatachi.pbt.scale=N` runs N times as many.
 */
class GutterStatePropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261005L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val path = "src/main/kotlin/a/File.kt"
    private val role = roleModel("domain.Service", layoutEntry(path))

    private fun lineOf(random: Random): String {
        val pieces = listOf("e: Architecture.kt:3", "  ", "Unresolved   reference", "\n", "\t", "x".repeat(random.nextInt(0, 300)), "日本語")
        return List(random.nextInt(0, 6)) { pieces[random.nextInt(pieces.size)] }.joinToString("")
    }

    private fun failureOf(random: Random): LoadFailure {
        val file = Paths.get("/w/definition.json")
        val details = List(random.nextInt(0, 4)) { lineOf(random) }
        return when (random.nextInt(8)) {
            0 -> LoadFailure.Gradle(GradleFailure.CompilationFailed(details))
            1 -> LoadFailure.Gradle(GradleFailure.TaskNotFound(":arch:task", details))
            2 -> LoadFailure.Gradle(GradleFailure.ArchitectureNotSet(details))
            3 -> LoadFailure.Gradle(GradleFailure.ProcessorRejected("E", details))
            4 -> LoadFailure.Gradle(GradleFailure.Other(details))
            5 -> LoadFailure.JsonMissing(file)
            6 -> LoadFailure.MalformedJson(file, lineOf(random))
            else -> LoadFailure.IncompatibleJson(file, lineOf(random), lineOf(random))
        }
    }

    private fun tooltip(freshness: Freshness, stale: Boolean): String {
        val roles = fileRoles(path, role, freshness = freshness)
        return GutterMarks.of(GutterInput(roles, emptyList(), emptyList(), SourceFileKind.Other, stale)).single().tooltip
    }

    // covers: 読み直しの失敗
    @Test
    fun `読み直しの失敗の行は 失敗のときだけ 2 行目に付き 1 行目と理由の長さは変わらない`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            val failure = failureOf(random)
            val stale = random.nextBoolean()
            val reasons = listOf(StaleReason.DefinitionChanged, StaleReason.ReloadFailed(failure))
            val reason = reasons[random.nextInt(reasons.size)]
            val withReason = tooltip(Freshness.Stale(DEFINITION_INSTANT, reason), stale)
            val fresh = tooltip(Freshness.Fresh, stale)

            val lines = withReason.lines()
            assertEquals("seed=$caseSeed the first line is the same whatever the reload did", fresh, lines.first())
            if (reason is StaleReason.ReloadFailed) {
                assertEquals("seed=$caseSeed $withReason", 2, lines.size)
                assertEquals("seed=$caseSeed", "The definition could not be read again: ${GutterTooltips.summary(failure)}", lines[1])
                assertFalse("seed=$caseSeed a summary is one line", GutterTooltips.summary(failure).contains('\n'))
                assertTrue("seed=$caseSeed ${GutterTooltips.summary(failure)}", GutterTooltips.summary(failure).length <= 120 || failure !is LoadFailure.Gradle)
                assertTrue("seed=$caseSeed", GutterTooltips.summary(failure).isNotBlank())
            } else {
                assertEquals("seed=$caseSeed $withReason", 1, lines.size)
            }
        }
    }

    // covers: NotLoaded
    @Test
    fun `状態の列で 読み込みを頼むのは 何も読んでいない状態か 読まない設定で止まった状態だけで 設定が切れていれば頼まない`() = runBlocking<Unit> {
        val snapshots = listOf(definitionSnapshot(definitionModel(role)))
        checkAll(config, Arb.long()) { walkSeed ->
            val walk = DefinitionStateScripts.random(Random(walkSeed), 40, snapshots)
            for (state in walk) {
                assertFalse("seed=$walkSeed $state", GutterFreshness.shouldEnsureLoaded(state, autoLoad = false))
                val asks = GutterFreshness.shouldEnsureLoaded(state, autoLoad = true)
                val waiting = state == DefinitionState.NotLoaded ||
                    (state is DefinitionState.Unavailable && state.reason == DefinitionUnavailableReason.LoadingWithoutUserDisabled)
                assertEquals("seed=$walkSeed $state", waiting, asks)
                if (state is DefinitionState.Loading || state is DefinitionState.Ready) assertFalse("seed=$walkSeed $state", asks)
            }
        }
    }
}
