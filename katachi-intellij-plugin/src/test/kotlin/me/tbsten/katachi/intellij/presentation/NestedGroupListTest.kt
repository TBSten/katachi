package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.testing.cast
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Nested groups (`"domain".group { "model".group { ... } }`): katachi's roleName is the group path
 * joined by `/` and the role's name, and the JSON lists roles in declaration order, so a child
 * group can sit between two roles of its parent and a root role can come after a group.
 */
class NestedGroupListTest {
    private val now = Instant.parse("2026-09-27T10:00:00Z")
    private val arch = module(":arch")
    private val other = module(":other")

    /** Declaration order of an architecture mixing root roles, parents' roles and child groups. */
    private val declared = listOf(
        "Readme",
        "domain/UseCase",
        "domain/model/Entity",
        "domain/model/value/Id",
        "domain/Service",
        "data/Repository",
        "domain/model/Mapper",
        "Changelog",
        "feature/home/Screen",
    ).map { template(it) }

    private fun ready(vararg modules: Pair<KatachiModule, List<TemplateModel>>) = KatachiScreenState(
        phase = ScreenPhase.Ready,
        modules = modules.map { it.first },
        snapshots = modules.map { (module, templates) -> DescriptionSnapshot(module, "0.3.0", templates, now) },
    )

    private fun list(state: KatachiScreenState): ListUi = uiStateOf(state, JapaneseKatachiStrings, now).body.cast<BodyUi.Listing>().list

    /** Headers as `# title`, rows as their role name, module bands as `== path`. */
    private fun outline(state: KatachiScreenState): List<String> = list(state).items.map { item ->
        when (item) {
            is ListItemUi.ModuleHeader -> "== ${item.title} ${item.counter}"
            is ListItemUi.GroupHeader -> "# ${item.title}"
            is ListItemUi.Row -> item.row.id.roleName
        }
    }

    @Test
    fun `入れ子のグループは親のあとに子を並べ同じ見出しを2回出さずパンくずで親子を示す`() {
        assertEquals(
            listOf(
                "Readme",
                "Changelog",
                "# domain",
                "domain/UseCase",
                "domain/Service",
                "# domain › model",
                "domain/model/Entity",
                "domain/model/Mapper",
                "# domain › model › value",
                "domain/model/value/Id",
                "# data",
                "data/Repository",
                "# feature › home",
                "feature/home/Screen",
            ),
            outline(ready(arch to declared)),
        )
    }

    @Test
    fun `見出しのキーはグループごとに1つで一覧の中で重ならない`() {
        val keys = list(ready(arch to declared)).items.map { it.key }
        assertEquals(keys.distinct(), keys)
    }

    @Test
    fun `生成とキー操作が使う行の順も画面の並びと同じになる`() {
        val state = ready(arch to declared)
        val shown = list(state).items.mapNotNull { (it as? ListItemUi.Row)?.row?.id }
        assertEquals(shown, state.rows.map { it.id })
    }

    @Test
    fun `検索で絞ると一致した行のグループの見出しだけを残す`() {
        val state = ready(arch to declared).let { applyFormIntent(it, KatachiIntent.Search("e"))!! }
        val hits = applyFormIntent(ready(arch to declared), KatachiIntent.Search("mapper"))!!
        assertEquals(listOf("# domain › model", "domain/model/Mapper"), outline(hits))
        // Every header left has a row under it.
        val lines = outline(state)
        lines.withIndex().filter { it.value.startsWith("#") }.forEach { (index, _) ->
            assertEquals(false, lines.getOrNull(index + 1)?.startsWith("#") ?: true)
        }
    }

    @Test
    fun `検索外の選択中の行も自分のグループの見出しの下に出る`() {
        val entity = TemplateId(arch.id, "domain/model/Entity")
        val state = ready(arch to declared)
            .let { applyFormIntent(it, KatachiIntent.ToggleCheck(entity))!! }
            .let { applyFormIntent(it, KatachiIntent.Search("repository"))!! }
        assertEquals(listOf("# domain › model", "domain/model/Entity", "# data", "data/Repository"), outline(state))
    }

    @Test
    fun `モジュールが2つなら帯の中で入れ子を並べ折りたたみと件数は入れ子でも数える`() {
        val state = ready(arch to declared, other to listOf(template("x/y/Z"), template("Top")))
            .let { applyFormIntent(it, KatachiIntent.ToggleCheck(TemplateId(arch.id, "domain/model/value/Id")))!! }
        val lines = outline(state)
        assertEquals("== :arch 1/9", lines.first())
        assertEquals(listOf("== :other 0/2", "Top", "# x › y", "x/y/Z"), lines.dropWhile { !it.startsWith("== :other") })

        val folded = applyFormIntent(state, KatachiIntent.ToggleModule(arch.id))!!
        assertEquals(listOf("== :arch 1/9", "== :other 0/2", "Top", "# x › y", "x/y/Z"), outline(folded))
    }
}
