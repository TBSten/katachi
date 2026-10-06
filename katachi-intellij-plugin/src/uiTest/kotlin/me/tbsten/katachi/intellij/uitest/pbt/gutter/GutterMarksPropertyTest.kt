package me.tbsten.katachi.intellij.uitest.pbt.gutter

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.presentation.gutter.GutterFreshness
import me.tbsten.katachi.intellij.presentation.gutter.GutterInput
import me.tbsten.katachi.intellij.presentation.gutter.GutterMarks
import me.tbsten.katachi.intellij.presentation.gutter.MarkPlacement
import me.tbsten.katachi.intellij.presentation.gutter.MarkTarget
import me.tbsten.katachi.intellij.presentation.gutter.SourceFileKind
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [GutterMarks] (design 2.1, 2.2) over random files, roles and explanations, and [GutterFreshness] over random walks of
 * the definition's states. `-Pkatachi.pbt.seed=N` tries another seed, `-Pkatachi.pbt.scale=N` runs N times as many.
 */
class GutterMarksPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261005L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val path = "src/main/kotlin/a/File.kt"
    private val roles = listOf("domain.Service", "api.Handler", "data.Repository").map { roleModel(it, layoutEntry(path)) }

    /** A file a pattern matched but whose captures held it back: no ◆ either, as for any file no role holds. */
    private fun captureMismatchOutcome(): MatchOutcome = MatchOutcome.CaptureMismatch(roles.first(), roles.first().layout.first(), emptyList(), expectedPath = null)

    private data class Scenario(val input: GutterInput, val restricting: Set<String>)

    private fun scenario(random: Random): Scenario {
        val file = BindingFiles.random(random, overloads = true)
        val held = roles.shuffled(random).take(random.nextInt(1, 3))
        val restricting = mutableSetOf<String>()
        val explanations = held.filter { random.nextBoolean() }.map { role ->
            val checked = random.nextBoolean()
            val unexplained = if (random.nextInt(5) == 0) "could not read the file" else null
            if (checked && unexplained == null) restricting += role.qualifiedName
            val explained = if (unexplained == null) file.explanations().filter { random.nextInt(4) != 0 } else emptyList()
            roleFileDeclarations(role, fileDeclarations(path, *explained.toTypedArray(), declarationsChecked = checked, unexplainedReason = unexplained))
        }
        val kind = SourceFileKind.entries[random.nextInt(SourceFileKind.entries.size)]
        val declarations = if (kind == SourceFileKind.Kotlin && random.nextInt(6) != 0) file.declarations else emptyList()
        val stale = random.nextBoolean()
        return Scenario(GutterInput(fileRoles(path, *held.toTypedArray()), explanations, declarations, kind, stale), restricting)
    }

    // covers: 置き場
    @Test
    fun `縛っている Role があり宣言があれば宣言ごと そうでなければファイルに 1 つ`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val (input, restricting) = scenario(Random(caseSeed))
            val marks = GutterMarks.of(input)
            val perDeclaration = input.fileKind == SourceFileKind.Kotlin && restricting.isNotEmpty() && input.declarations.isNotEmpty()
            if (perDeclaration) {
                assertEquals("seed=$caseSeed", input.declarations.size, marks.size)
                assertTrue("seed=$caseSeed", marks.all { it.placement == MarkPlacement.PerDeclaration })
                assertEquals("seed=$caseSeed", input.declarations, marks.map { (it.target as? MarkTarget.Declaration)?.declaration })
            } else {
                assertEquals("seed=$caseSeed", 1, marks.size)
                assertEquals(MarkPlacement.OnePerFile, marks.single().placement)
                val expectedTarget = input.declarations.minByOrNull { it.line }?.takeIf { input.fileKind == SourceFileKind.Kotlin }
                    ?.let(MarkTarget::Declaration) ?: MarkTarget.FileHead
                assertEquals("seed=$caseSeed", expectedTarget, marks.single().target)
            }
        }
    }

    // covers: ツールチップ
    @Test
    fun `ツールチップは Role の名前で始まり 説明があるときだけ古さの印が付く`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val (input, _) = scenario(Random(caseSeed))
            for (mark in GutterMarks.of(input)) {
                val names = mark.roles.joinToString(", ") { it.role.qualifiedName }
                assertTrue("seed=$caseSeed ${mark.tooltip}", mark.tooltip.startsWith("katachi: $names"))
                val explained = mark.roles.any { it.explanation != null }
                assertEquals("seed=$caseSeed ${mark.tooltip}", input.stale && explained, mark.tooltip.endsWith(" (stale)"))
                assertEquals("seed=$caseSeed ${mark.tooltip}", explained, mark.tooltip.contains(" — "))
                if (mark.placement == MarkPlacement.OnePerFile) assertFalse("seed=$caseSeed a ◆ of the file explains nothing", explained)
            }
        }
    }

    // covers: 当たらないファイル
    @Test
    fun `Matched でないファイルには何も出さない`() = runBlocking<Unit> {
        val outcomes = listOf(MatchOutcome.Unexpected, MatchOutcome.IgnoredByVcs, MatchOutcome.NotWalked(".git"), MatchOutcome.UnexpectedUnder("a"), captureMismatchOutcome())
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            val (input, _) = scenario(random)
            val outcome = outcomes[random.nextInt(outcomes.size)]
            val unmatched = input.copy(roles = input.roles?.copy(outcome = outcome))
            assertEquals("seed=$caseSeed $outcome", emptyList<Any>(), GutterMarks.of(unmatched))
            assertEquals(emptyList<Any>(), GutterMarks.of(input.copy(roles = null)))
        }
    }

    // covers: 状態の列
    @Test
    fun `状態の列で Loading への遷移では描き直さず 答えが替わる遷移では描き直す`() = runBlocking<Unit> {
        val snapshots = listOf(
            definitionSnapshot(definitionModel(roles[0])),
            definitionSnapshot(definitionModel(roles[1])),
        )
        checkAll(config, Arb.long()) { walkSeed ->
            val walk = DefinitionStateScripts.random(Random(walkSeed), 40, snapshots)
            for ((before, after) in walk.zipWithNext()) {
                val redraw = GutterFreshness.shouldRedraw(before, after)
                if (after is DefinitionState.Loading) assertFalse("seed=$walkSeed $before → $after", redraw)
                if (after !is DefinitionState.Loading && before != after) assertTrue("seed=$walkSeed $before → $after", redraw)
            }
        }
    }
}
