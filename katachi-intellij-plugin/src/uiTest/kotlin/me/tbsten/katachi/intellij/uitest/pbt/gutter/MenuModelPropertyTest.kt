package me.tbsten.katachi.intellij.uitest.pbt.gutter

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DeclarationModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.gutter.BoundExplanation
import me.tbsten.katachi.intellij.presentation.gutter.MenuAvailability
import me.tbsten.katachi.intellij.presentation.gutter.MenuInput
import me.tbsten.katachi.intellij.presentation.gutter.MenuItem
import me.tbsten.katachi.intellij.presentation.gutter.MenuModel
import me.tbsten.katachi.intellij.presentation.gutter.MenuTarget
import me.tbsten.katachi.intellij.presentation.gutter.RoleMenuInput
import me.tbsten.katachi.intellij.presentation.gutter.SiblingFiles
import me.tbsten.katachi.intellij.presentation.gutter.TemplateChoice
import me.tbsten.katachi.intellij.testing.DEFINITION_ROOT
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [MenuModel] (design 2.3) over random ◆: whatever the explanations, siblings, templates, unfound sites and available
 * actions, the menu keeps its shape. `-Pkatachi.pbt.seed=N` tries another seed, `-Pkatachi.pbt.scale=N` runs N times as many.
 */
class MenuModelPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261005L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val module = module(":arch")
    private val roleNames = listOf("domain.Service", "api.Handler", "data.Repository")

    private fun site(random: Random) = DeclarationSiteRef("R${random.nextInt(4)}.kt", random.nextInt(1, 100))

    private fun rule(random: Random, withSite: Boolean = random.nextInt(4) != 0) =
        RuleRef("class C${random.nextInt(9)}", if (withSite) site(random) else null)

    private fun explanation(random: Random): BoundExplanation? = when (random.nextInt(4)) {
        0 -> null
        1 -> BoundExplanation.NotComputed
        else -> BoundExplanation.Explained(
            DeclarationModel(
                SourceDeclarationKind.Class,
                "C",
                1,
                listOf(DeclarationVerdict.Allowed, DeclarationVerdict.AllowedByAnythingElse, DeclarationVerdict.Prohibited, DeclarationVerdict.Mismatched, DeclarationVerdict.Unexpected, DeclarationVerdict.Unknown("Future"))[random.nextInt(6)],
                List(random.nextInt(0, 3)) { rule(random) },
                "differs",
            ),
        )
    }

    private fun role(random: Random, name: String): RoleMenuInput {
        val files = List(random.nextInt(0, 30)) { "dir${random.nextInt(3)}/F${random.nextInt(40)}.kt" }
        val entries = listOf(layoutEntry("**.kt", me.tbsten.katachi.intellij.model.LayoutEntryKind.AnyFile, matchedFiles = files))
        return RoleMenuInput(
            role = roleModel(name, *entries.toTypedArray()).let { if (random.nextBoolean()) it else it.copy(declaredAt = null) },
            explanation = explanation(random),
            constraintSite = if (random.nextBoolean()) site(random) else null,
            missing = List(random.nextInt(0, 3)) { rule(random) },
            siblings = SiblingFiles.of("dir0/F0.kt", entries),
            projectRoot = DEFINITION_ROOT,
            templates = List(random.nextInt(0, 4)) { TemplateChoice(TemplateId(module.id, "$name.t$it"), "t$it") },
        )
    }

    private data class Scenario(val input: MenuInput, val availability: MenuAvailability)

    private fun scenario(random: Random): Scenario {
        val roles = roleNames.shuffled(random).take(random.nextInt(1, 3)).map { role(random, it) }
        val bare = MenuInput("title", DEFINITION_ROOT.resolve("dir0/F0.kt"), module, random.nextBoolean(), roles)
        val sites = MenuModel.of(bare, ALL).sites.map { it.site }
        val unresolved = sites.filter { random.nextInt(3) == 0 }.associateWith { "gone" }
        val availability = MenuAvailability(random.nextBoolean(), random.nextBoolean(), random.nextBoolean())
        return Scenario(bare.copy(unresolved = unresolved), availability)
    }

    private val ALL = MenuAvailability(runCheck = true, refresh = true, checkRunning = false)

    // covers: 行の形
    @Test
    fun `押せる行には飛び先があり 見つからない宣言元の行は押せず その理由を右に言う`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val (input, availability) = scenario(Random(caseSeed))
            val menu = MenuModel.of(input, availability)
            for (row in menu.rows()) {
                val where = "seed=$caseSeed $row"
                if (row.enabled) assertTrue(where, row.target != null)
                val target = row.target
                if (target is MenuTarget.Site && target.site in input.unresolved) {
                    assertFalse(where, row.enabled)
                    assertEquals(where, "(declaration not found: gone)", row.trailing)
                }
                if (target is MenuTarget.Site && target.site !in input.unresolved) assertTrue(where, row.enabled)
            }
            // The sites are known before anything is resolved: what is unfound or available changes no row's place.
            assertEquals("seed=$caseSeed", MenuModel.of(input.copy(unresolved = emptyMap()), ALL).sites, menu.sites)
        }
    }

    // covers: 検査と更新
    @Test
    fun `検査の行は 1 つで 押せるのは アクションがあり走っていないときだけ 更新は定義が古ければ先頭にある`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val (input, availability) = scenario(Random(caseSeed))
            val menu = MenuModel.of(input, availability)
            val where = "seed=$caseSeed ${menu.items}"
            val runs = menu.rows().filter { it.target == MenuTarget.RunCheck }
            assertEquals(where, 1, runs.size)
            assertEquals(where, availability.runCheck && !availability.checkRunning, runs.single().enabled)
            assertEquals(where, availability.checkRunning, runs.single().text.contains("Checking"))
            val needsRefresh = input.roles.any { it.explanation == BoundExplanation.NotComputed }
            val plain = menu.rows().filter { it.target == MenuTarget.Refresh && !it.text.contains("to see") }
            assertEquals(where, 1, plain.size)
            assertEquals(where, availability.refresh, plain.single().enabled)
            val first = menu.items.first()
            assertEquals(where, input.stale && !needsRefresh, first is MenuItem.Row && first.text == "⟳ Reload Definition")
            // The check is above the plain Refresh unless that was moved to the top.
            if (!(input.stale && !needsRefresh)) assertEquals(where, runs.single(), menu.rows().dropLast(1).last())
        }
    }

    // covers: 兄弟と次のファイル
    @Test
    fun `兄弟の行は 15 件まで このファイルのパスを指さず 次のファイルの行は Role ごとに テンプレートの数だけ`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val (input, availability) = scenario(Random(caseSeed))
            val menu = MenuModel.of(input, availability)
            val where = "seed=$caseSeed"
            val opens = menu.rows().mapNotNull { (it.target as? MenuTarget.OpenFile)?.path }
            assertFalse(where, input.file in opens)
            assertTrue(where, opens.all { it.startsWith(DEFINITION_ROOT) })
            assertEquals(where, input.roles.sumOf { it.siblings.files.size }, opens.size)
            assertTrue(where, input.roles.all { it.siblings.files.size <= SiblingFiles.LIMIT })
            val generates = menu.rows().mapNotNull { it.target as? MenuTarget.Generate }
            assertEquals(where, input.roles.flatMap { it.templates }.map { it.id }, generates.map { it.template })
            assertTrue(where, generates.all { it.file == input.file })
        }
    }
}
