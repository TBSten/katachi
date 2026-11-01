package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.declarations
import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.englishAuthoring
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.withTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Paths

/** What the ◆ of a role and of a `.template { }` say and open (ide-authoring design 4.3). */
class DefinitionGutterMarksTest {
    private val arch = module(":arch").id
    private val file = "ServiceRole.kt"
    private val root = Paths.get("/w/repo")
    private val definitionFile = root.resolve("arch/src/test/kotlin/ServiceRole.kt")
    private val both = DefinitionMenuAvailability(runCheck = true, refresh = true)

    private fun files(n: Int) = (1..n).map { "core/service/Service$it.kt" }

    private fun role(matched: List<String> = files(3), templated: Boolean = true, truncated: Boolean = false, count: Int? = null) = roleModel(
        "domain.Service",
        layoutEntry("core/service/*.kt", matchedFiles = matched).declaredAt(file, 3).let { entry ->
            val sized = entry.copy(truncated = truncated, matchedCount = count ?: entry.matchedCount)
            if (templated) sized.copy(template = me.tbsten.katachi.intellij.model.TemplateRef("domain.Service.service", "service", "Service", me.tbsten.katachi.intellij.model.DeclarationSiteRef(file, 4))) else sized
        },
    ).declaredAt(file, 2)

    private fun bound(kind: AnchorKind, name: String, line: Int, vararg roles: me.tbsten.katachi.intellij.model.RoleModel): BoundAnchor =
        AnchorBinding.bind(listOf(Anchor(kind, name, line)), definitionModel(*roles).declarations(arch)).single()

    private fun mark(role: me.tbsten.katachi.intellij.model.RoleModel = role(), stale: Boolean = false) =
        DefinitionGutterMarks.of(bound(AnchorKind.Role, "Service", 2, role), definitionFile, root, stale, englishAuthoring)!!

    private fun shown(model: DefinitionMenuModel) = model.items.map {
        when (it) {
            is DefinitionMenuItem.Row -> it.text + (it.trailing?.let { t -> " | $t" } ?: "") + if (it.enabled) "" else " (disabled)"
            is DefinitionMenuItem.Section -> "== ${it.title}"
            is DefinitionMenuItem.Submenu -> "${it.text} > ${it.rows.map { r -> r.text }}"
            DefinitionMenuItem.Separator -> "---"
        }
    }

    @Test
    fun `役割の ◆ は 役割の名前と ファイルの数を言う`() {
        assertEquals("katachi: domain.Service — 3 files", mark().tooltip)
        assertEquals("katachi: domain.Service — 1 file", mark(role(files(1))).tooltip)
        assertEquals("katachi: domain.Service — no files", mark(role(emptyList())).tooltip)
    }

    @Test
    fun `古い答えの ◆ は tooltip に stale と メニューの先頭に読み直しを持つ`() {
        val stale = mark(stale = true)

        assertEquals("katachi: domain.Service — 3 files (stale)", stale.tooltip)
        assertEquals("⟳ Reload Definition", shown(stale.menu(both, englishAuthoring)).first())
    }

    @Test
    fun `役割の menu は ファイル 生成 チェック 読み直しの順`() {
        val menu = mark().menu(both, englishAuthoring)

        assertEquals(
            listOf(
                "Service1.kt | core/service",
                "Service2.kt | core/service",
                "Service3.kt | core/service",
                "+ Generate from this template…",
                "---",
                "▶ Run the check (:arch:test)",
                "⟳ Reload Definition",
            ),
            shown(menu),
        )
        assertEquals(DefinitionMenuTarget.OpenFile(root.resolve("core/service/Service2.kt")), menu.rows()[1].target)
        assertEquals(DefinitionMenuTarget.Generate(TemplateId(arch, "domain.Service.service"), definitionFile), menu.rows()[3].target)
    }

    @Test
    fun `ファイルが無い役割は その旨の行を出し template が無ければ Generate も出さない`() {
        val menu = mark(role(emptyList(), templated = false)).menu(both, englishAuthoring)

        assertEquals(listOf("— No file yet (disabled)", "---", "▶ Run the check (:arch:test)", "⟳ Reload Definition"), shown(menu))
    }

    @Test
    fun `200 個を超える分は 残りの数と ツールウィンドウへの案内になる`() {
        val menu = mark(role(files(250), templated = false)).menu(both, englishAuthoring)

        assertEquals(200 + 1, menu.items.takeWhile { it !is DefinitionMenuItem.Separator }.size)
        assertEquals("… and 50 more (open the tool window)", (menu.items[200] as DefinitionMenuItem.Row).text)
        assertNull((menu.items[200] as DefinitionMenuItem.Row).target)
    }

    @Test
    fun `一覧が打ち切られた役割の数は 重複を数えず 数えた件数の和にする`() {
        val truncated = role(files(2), templated = false, truncated = true, count = 900)

        assertEquals("katachi: domain.Service — 900 files", mark(truncated).tooltip)
    }

    @Test
    fun `template が複数あれば Generate は サブメニューになる`() {
        val two = roleModel(
            "domain.Service",
            layoutEntry("a/*.kt", index = 0).declaredAt(file, 3).withTemplate("domain.Service.a", file, 4),
            layoutEntry("b/*.kt", index = 1).declaredAt(file, 5).withTemplate("domain.Service.b", file, 6, title = "B"),
        ).declaredAt(file, 2)

        val menu = mark(two).menu(both, englishAuthoring)

        val generate = menu.items.filterIsInstance<DefinitionMenuItem.Submenu>().single()
        assertEquals(listOf("domain.Service.a", "B"), generate.rows.map { it.text })
    }

    @Test
    fun `チェックと読み直しの action が無ければ その行は選べない`() {
        val menu = mark().menu(DefinitionMenuAvailability(runCheck = false, refresh = false), englishAuthoring)

        assertEquals(listOf(false, false), menu.rows().takeLast(2).map { it.enabled })
    }

    @Test
    fun `template の ◆ は その template から生成する行と ツールウィンドウで見る行だけ`() {
        val template = DefinitionGutterMarks.of(bound(AnchorKind.Template, "*.kt", 4, role()), definitionFile, root, stale = false, englishAuthoring)!!

        assertEquals("katachi: generate from domain.Service", template.tooltip)
        assertEquals(listOf("+ Generate from this template…", "⊞ Show in the tool window"), shown(template.menu(both, englishAuthoring)))
        val id = TemplateId(arch, "domain.Service.service")
        assertEquals(listOf(DefinitionMenuTarget.Generate(id, definitionFile), DefinitionMenuTarget.Reveal(id)), template.menu(both, englishAuthoring).rows().map { it.target })
    }

    @Test
    fun `entry の anchor と 何にも結ばれない anchor には ◆ が無い`() {
        assertNull(DefinitionGutterMarks.of(bound(AnchorKind.Entry, "*.kt", 3, role()), definitionFile, root, false, englishAuthoring))
        assertNull(DefinitionGutterMarks.of(bound(AnchorKind.Role, "Nope", 2, role()), definitionFile, root, false, englishAuthoring))
    }

    @Test
    fun `同じファイルが2つの entry に当たっていても 役割のファイルの数は1回だけ数える`() {
        val overlapping = roleModel(
            "domain.Service",
            layoutEntry("a/*.kt", matchedFiles = listOf("a/X.kt", "a/Y.kt"), index = 0).declaredAt(file, 3),
            layoutEntry("a/*.kt", matchedFiles = listOf("a/Y.kt", "a/Z.kt"), index = 1).declaredAt(file, 4),
        ).declaredAt(file, 2)

        assertEquals("katachi: domain.Service — 3 files", mark(overlapping).tooltip)
    }

    @Test
    fun `template の ◆ は 同じ役割の別の template ではなく 自分の template から生成する`() {
        val two = roleModel(
            "domain.Service",
            layoutEntry("a/*Service.kt", index = 0).declaredAt(file, 3).withTemplate("domain.Service.a", file, 4),
            layoutEntry("b/*Repo.kt", index = 1).declaredAt(file, 5).withTemplate("domain.Service.b", file, 6),
        ).declaredAt(file, 2)

        val second = DefinitionGutterMarks.of(bound(AnchorKind.Template, "*Repo.kt", 6, two), definitionFile, root, stale = false, englishAuthoring)!!

        assertEquals(
            listOf(DefinitionMenuTarget.Generate(TemplateId(arch, "domain.Service.b"), definitionFile), DefinitionMenuTarget.Reveal(TemplateId(arch, "domain.Service.b"))),
            second.menu(both, englishAuthoring).rows().map { it.target },
        )
    }

    @Test
    fun `件数が分からない役割は 0 件ではなく 件数不明と言い メニューも ファイルが無いとは言わない`() {
        val unknown = roleModel(
            "domain.Service",
            layoutEntry("core/service/*.kt").declaredAt(file, 3).let { it.copy(matchedCount = null) },
        ).declaredAt(file, 2)

        val unknownMark = mark(unknown)

        assertEquals("katachi: domain.Service — ? files", unknownMark.tooltip)
        assertNull(unknownMark.roles.single().count)
        assertEquals("— File count unknown (the check has not run) (disabled)", shown(unknownMark.menu(both, englishAuthoring)).first())
    }

    @Test
    fun `役割が2つ結ばれていて 片方の件数が分からなければ 合計も分からない`() {
        val known = roleModel("a.Service", layoutEntry("a/*.kt", matchedFiles = files(2)).declaredAt(file, 3)).declaredAt(file, 2)
        val unknown = roleModel("b.Service", layoutEntry("b/*.kt").declaredAt(file, 3).let { it.copy(matchedCount = null) }).declaredAt(file, 2)

        val both2 = DefinitionGutterMarks.of(bound(AnchorKind.Role, "Service", 2, known, unknown), definitionFile, root, false, englishAuthoring)!!

        assertEquals("katachi: a.Service, b.Service — ? files", both2.tooltip)
    }

    // The same helper function called for two groups: two roles, written at the same lines of the definition file.
    private fun twoRolesOfOneHelper() = listOf("a", "b").map { group ->
        roleModel("$group.Service", layoutEntry("$group/*Service.kt", matchedFiles = listOf("$group/X.kt")).declaredAt(file, 3).withTemplate("$group.Service.service", file, 4, title = "Service")).declaredAt(file, 2)
    }.toTypedArray()

    @Test
    fun `同じ template の行に 2つの役割が結ばれたら 両方の template を サブメニューで選べ ツールウィンドウでも両方見られる`() {
        val roles = twoRolesOfOneHelper()

        val template = DefinitionGutterMarks.of(bound(AnchorKind.Template, "*Service.kt", 4, *roles), definitionFile, root, stale = false, englishAuthoring)!!

        assertEquals("katachi: generate from a.Service, b.Service", template.tooltip)
        val menu = template.menu(both, englishAuthoring)
        val submenus = menu.items.filterIsInstance<DefinitionMenuItem.Submenu>()
        assertEquals(listOf("+ Generate from this template…", "⊞ Show in the tool window"), submenus.map { it.text })
        assertEquals(listOf("a.Service: Service", "b.Service: Service"), submenus[0].rows.map { it.text })
        assertEquals(
            listOf("a.Service.service", "b.Service.service").map { DefinitionMenuTarget.Generate(TemplateId(arch, it), definitionFile) },
            submenus[0].rows.map { it.target },
        )
        assertEquals(
            listOf("a.Service.service", "b.Service.service").map { DefinitionMenuTarget.Reveal(TemplateId(arch, it)) },
            submenus[1].rows.map { it.target },
        )
    }

    @Test
    fun `役割の ◆ に2つの役割が結ばれたら それぞれの template だけを それぞれの節に出す`() {
        val role = DefinitionGutterMarks.of(bound(AnchorKind.Role, "Service", 2, *twoRolesOfOneHelper()), definitionFile, root, stale = false, englishAuthoring)!!

        assertEquals("katachi: a.Service, b.Service — 2 files", role.tooltip)
        assertEquals(listOf("a.Service.service"), role.roles[0].templates.map { it.id.template })
        assertEquals(listOf("b.Service.service"), role.roles[1].templates.map { it.id.template })
    }
}
