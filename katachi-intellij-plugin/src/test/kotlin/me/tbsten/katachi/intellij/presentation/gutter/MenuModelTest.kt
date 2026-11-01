package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DeclarationModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ALL_ACTIONS
import me.tbsten.katachi.intellij.testing.DEFINITION_ROOT
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.declarationModel
import me.tbsten.katachi.intellij.testing.englishGutter
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.markOf
import me.tbsten.katachi.intellij.testing.menuInputOf
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.shown
import me.tbsten.katachi.intellij.testing.templateRef
import me.tbsten.katachi.intellij.testing.withTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rows of the ◆ menu (design 2.3): their order, text, targets and when each can be chosen. */
class MenuModelTest {
    private val path = GutterFixtures.SERVICE_PATH
    private val legacy = "src/main/kotlin/com/example/service/LegacyStatusService.kt"
    private val service: RoleModel = roleModel("domain.Service", layoutEntry(path, matchedFiles = listOf(path, legacy))).withTemplates(templateRef(roleModel("domain.Service"), "service"))
    private val serviceNoTemplate: RoleModel = roleModel("domain.Service", layoutEntry(path, matchedFiles = listOf(path, legacy)))
    private val healthService = SourceDeclaration(SourceDeclarationKind.Class, "HealthService", 7)
    private val healthCache = SourceDeclaration(SourceDeclarationKind.Class, "HealthCache", 13)
    private val module = module(":arch")

    private fun explained(role: RoleModel, vararg declarations: DeclarationModel, missing: List<RuleRef> = emptyList(), constraintAt: DeclarationSiteRef? = DeclarationSiteRef("ServiceRole.kt", 73)): RoleFileDeclarations =
        roleFileDeclarations(role, fileDeclarations(path, *declarations, missing = missing)).let { it.copy(constraint = it.constraint.copy(declaredAt = constraintAt)) }

    private val allowedHealthService = declarationModel(SourceDeclarationKind.Class, "HealthService", 7, rules = listOf(GutterFixtures.SERVICE_RULE))

    private fun menu(
        role: RoleModel = service,
        declarations: List<DeclarationModel> = listOf(allowedHealthService),
        sources: List<SourceDeclaration> = listOf(healthService),
        stale: Boolean = false,
        missing: List<RuleRef> = emptyList(),
        unresolved: Map<DeclarationSiteRef, String> = emptyMap(),
        availability: MenuAvailability = ALL_ACTIONS,
        index: Int = 0,
    ): MenuModel {
        val roles = fileRoles(path, role)
        val mark = markOf(roles, sources, listOf(explained(role, *declarations.toTypedArray(), missing = missing)), stale, index)
        return MenuModel.of(menuInputOf(mark, roles, unresolved), availability, englishGutter)
    }

    @Test
    fun `許可された宣言のメニューは 許可元 Role 兄弟 次のファイル 検査 更新 の順`() {
        assertEquals(
            listOf(
                "◆ Allowed by class HealthService | ServiceRole.kt:80",
                "◇ Role domain.Service | ServiceRole.kt:1",
                "== Other files of domain.Service (1)",
                "LegacyStatusService.kt | src/main/kotlin/com/example/service",
                "-----",
                "+ New domain.Service from template…",
                "▶ Run the check (:arch:test)",
                "⟳ Reload Definition",
            ),
            menu().shown(),
        )
        assertEquals("HealthService — domain.Service", menu().title)
    }

    @Test
    fun `許可元の行は許可リストの行の宣言元へ 兄弟は開くファイルへ`() {
        val rows = menu().rows()
        assertEquals(MenuTarget.Site(module.id, GutterFixtures.SERVICE_RULE.declaredAt!!), rows[0].target)
        assertEquals(MenuTarget.Site(module.id, DeclarationSiteRef("ServiceRole.kt", 1)), rows[1].target)
        assertEquals(MenuTarget.OpenFile(DEFINITION_ROOT.resolve(legacy)), rows[2].target)
        assertEquals(MenuTarget.Generate(TemplateId(module.id, "domain.Service.service"), DEFINITION_ROOT.resolve(path)), rows[3].target)
        assertEquals(MenuTarget.RunCheck, rows[4].target)
        assertEquals(MenuTarget.Refresh, rows[5].target)
    }

    @Test
    fun `許可リストの行が 2 つ当たれば 最初の行に プラス 1 を付ける`() {
        val two = allowedHealthService.copy(rules = listOf(GutterFixtures.SERVICE_RULE, RuleRef("class *Service", DeclarationSiteRef("ServiceRole.kt", 82))))
        assertEquals("◆ Allowed by class HealthService +1 | ServiceRole.kt:80", menu(declarations = listOf(two)).shown().first())
    }

    @Test
    fun `どの行も許さない宣言は ktFileContent の行へ`() {
        val unexpected = declarationModel(SourceDeclarationKind.Class, "HealthCache", 13, DeclarationVerdict.Unexpected)
        val menu = menu(declarations = listOf(allowedHealthService, unexpected), sources = listOf(healthService, healthCache), index = 1)
        assertEquals("◆ Not allowed — see ktFileContent | ServiceRole.kt:73", menu.shown().first())
        assertEquals(MenuTarget.Site(module.id, DeclarationSiteRef("ServiceRole.kt", 73)), menu.rows().first().target)
        assertEquals("HealthCache — domain.Service", menu.title)
    }

    @Test
    fun `禁止の行と形の違う行も その行の宣言元へ`() {
        val prohibited = declarationModel(SourceDeclarationKind.Class, "HealthCache", 13, DeclarationVerdict.Prohibited, rules = listOf(RuleRef("not.class *Cache", DeclarationSiteRef("ServiceRole.kt", 85))))
        val mismatched = declarationModel(SourceDeclarationKind.Class, "HealthCache", 13, DeclarationVerdict.Mismatched, rules = listOf(GutterFixtures.SERVICE_RULE), difference = "must be public")
        fun first(declaration: DeclarationModel) = menu(declarations = listOf(declaration), sources = listOf(healthCache)).shown().first()
        assertEquals("◆ Prohibited by not.class *Cache | ServiceRole.kt:85", first(prohibited))
        assertEquals("◆ Does not match class HealthService: must be public | ServiceRole.kt:80", first(mismatched))
    }

    @Test
    fun `定義の後に足した宣言は 更新すると許可元が分かると言って 更新に飛ばす`() {
        val menu = menu(declarations = emptyList())
        assertEquals("⟳ Reload Definition to see what allows this", menu.rows().first().text)
        assertEquals(MenuTarget.Refresh, menu.rows().first().target)
    }

    @Test
    fun `足りない行はこのファイルで足りない節に並び 宣言元へ飛べる`() {
        val missing = listOf(RuleRef("class DefaultGetUser", DeclarationSiteRef("ServiceRole.kt", 82)))
        val shown = menu(missing = missing).shown()
        assertEquals(
            "the Role row is cut off from the missing rules, or it reads as one more of them",
            listOf("== Missing in this file (1)", "class DefaultGetUser | ServiceRole.kt:82", "-----", "◇ Role domain.Service | ServiceRole.kt:1"),
            shown.subList(1, 5),
        )
        assertFalse("no divider when nothing is missing", menu().shown().contains("== Missing in this file (0)"))
        assertEquals("◇ Role domain.Service | ServiceRole.kt:1", menu().shown()[1])
    }

    @Test
    fun `定義が古いと 更新が先頭に来て 末尾の更新は無くなる`() {
        val shown = menu(stale = true).shown()
        assertEquals("⟳ Reload Definition", shown.first())
        assertEquals(1, shown.count { it == "⟳ Reload Definition" })
        assertEquals("▶ Run the check (:arch:test)", shown.last())
        assertTrue("files made since are not listed: $shown", shown.contains("== Other files of domain.Service (1, stale)"))
        assertTrue(menu(stale = false).shown().contains("== Other files of domain.Service (1)"))
    }

    @Test
    fun `古くても 更新しないと分からない宣言の行があれば 更新の行は重ねない`() {
        val shown = menu(declarations = emptyList(), stale = true).shown()
        assertEquals("⟳ Reload Definition to see what allows this", shown.first())
        assertEquals("⟳ Reload Definition", shown.last())
    }

    @Test
    fun `テンプレートが無ければ 次のファイルの行は無く 2 つ以上なら入れ子のメニューになる`() {
        assertFalse(menu(role = serviceNoTemplate).shown().any { it.startsWith("+ New") })
        val base = roleModel("domain.Service", layoutEntry(path, matchedFiles = listOf(path)))
        val two = base.withTemplates(templateRef(base, "service"), templateRef(base, "fake", title = "Fake service"))
        val menu = menu(role = two)
        val submenu = menu.items.filterIsInstance<MenuItem.Submenu>().single()
        assertEquals("+ New domain.Service from template…", submenu.text)
        assertEquals(listOf("domain.Service.service", "Fake service"), submenu.rows.map { it.text })
        assertEquals(listOf(TemplateId(module.id, "domain.Service.service"), TemplateId(module.id, "domain.Service.fake")), submenu.rows.map { (it.target as MenuTarget.Generate).template })
    }

    @Test
    fun `同じテンプレートを持つ entry が複数あっても 1 つにまとめる`() {
        val base = roleModel("domain.Service", layoutEntry(path, matchedFiles = listOf(path)), layoutEntry("other.kt"))
        val same = templateRef(base, "service")
        val role = base.copy(layout = base.layout.map { it.copy(template = same) })
        assertEquals(1, menu(role = role).rows().count { it.target is MenuTarget.Generate })
    }

    @Test
    fun `兄弟が無ければ まだ無いと言い 見つからなかった宣言元の行は押せない`() {
        val alone = roleModel("domain.Service", layoutEntry(path, matchedFiles = listOf(path)))
        assertTrue(menu(role = alone).shown().containsAll(listOf("== Other files of domain.Service", "(disabled) — No other file yet")))
        val rule = GutterFixtures.SERVICE_RULE.declaredAt!!
        val shown = menu(unresolved = mapOf(rule to "no file of that name")).shown().first()
        assertEquals("(disabled) ◆ Allowed by class HealthService | (declaration not found: no file of that name)", shown)
    }

    @Test
    fun `15 件を超える兄弟は 残りの件数の押せない行で終わる`() {
        val files = (1..20).map { "src/main/kotlin/com/example/service/S$it.kt" }
        val many = roleModel("domain.Service", layoutEntry(path, matchedFiles = listOf(path) + files))
        val shown = menu(role = many).shown()
        assertTrue(shown.contains("== Other files of domain.Service (20)"))
        assertEquals(15, shown.count { Regex("S\\d+\\.kt \\| .*").matches(it) })
        assertTrue(shown.contains("(disabled) … and 5 more"))
    }

    @Test
    fun `検査が走っている間は 検査の行が検査中で押せず アクションが無ければ検査も更新も押せない`() {
        assertTrue(menu(availability = ALL_ACTIONS.copy(checkRunning = true)).shown().contains("(disabled) ▶ Checking…"))
        val none = menu(availability = MenuAvailability(runCheck = false, refresh = false, checkRunning = false)).shown()
        assertEquals(listOf("(disabled) ▶ Run the check (:arch:test)", "(disabled) ⟳ Reload Definition"), none.takeLast(2))
        val runCheck = menu(availability = ALL_ACTIONS.copy(refresh = false)).rows()
        assertTrue(runCheck.first { it.target == MenuTarget.RunCheck }.enabled)
        assertFalse(runCheck.first { it.target == MenuTarget.Refresh }.enabled)
    }

    @Test
    fun `ファイルに 1 つの ◆ には許可元の行が無く Role の行から始まる`() {
        val roles = fileRoles(GutterFixtures.REPOSITORY_PATH, roleModel("data.Repository", layoutEntry(GutterFixtures.REPOSITORY_PATH)))
        val mark = markOf(roles, listOf(SourceDeclaration(SourceDeclarationKind.Class, "HealthRepository", 5)))
        val menu = MenuModel.of(menuInputOf(mark, roles), ALL_ACTIONS, englishGutter)
        assertEquals("◇ Role data.Repository | RepositoryRole.kt:1", menu.shown().first())
        assertEquals("HealthRepository.kt — data.Repository", menu.title)
        assertEquals(setOf(MenuTarget.Site(module.id, DeclarationSiteRef("RepositoryRole.kt", 1))), menu.sites)
    }

    @Test
    fun `Role が 2 つ当たれば Role ごとの節に分け 検査と更新は 1 度だけ`() {
        val handler = roleModel("api.Handler", layoutEntry(path, matchedFiles = listOf(path, "src/api/Other.kt")))
        val roles = fileRoles(path, service, handler)
        val mark = markOf(roles, listOf(healthService), listOf(explained(service, allowedHealthService), explained(handler, allowedHealthService)))
        val shown = MenuModel.of(menuInputOf(mark, roles), ALL_ACTIONS, englishGutter).shown()
        assertEquals(listOf("== domain.Service", "== api.Handler"), shown.filter { it.startsWith("== ") && !it.startsWith("== Other") })
        assertEquals(1, shown.count { it.startsWith("▶ Run") })
        assertEquals(1, shown.count { it == "⟳ Reload Definition" })
        assertEquals(1, shown.count { it.startsWith("+ New domain.Service") })
    }

    @Test
    fun `押せる行の飛び先の宣言元は 組み立てる前に分かる`() {
        val sites = menu(missing = listOf(RuleRef("class X", DeclarationSiteRef("ServiceRole.kt", 82)))).sites.map { it.site }
        assertEquals(
            setOf(GutterFixtures.SERVICE_RULE.declaredAt, DeclarationSiteRef("ServiceRole.kt", 82), DeclarationSiteRef("ServiceRole.kt", 1)),
            sites.toSet(),
        )
    }
}
