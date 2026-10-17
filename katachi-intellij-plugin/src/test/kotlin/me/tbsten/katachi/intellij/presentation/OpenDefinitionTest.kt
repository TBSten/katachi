package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeIdeEffects
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** ⋯ › Open Definition (design T-10): who gets the menu item, and what the IDE is asked to open. */
class OpenDefinitionTest {
    private val service = template("domain.Service")
    private val definitionModule = module(":arch").copy(capabilities = setOf(KatachiCapability.Definition, KatachiCapability.Templates))
    private val legacyModule = module(":old")

    private fun snapshot(module: KatachiModule, withDefinition: Boolean) = DescriptionSnapshot(
        module,
        "0.3.0",
        listOf(service),
        DEFINITION_INSTANT,
        definitionModel(roleModel("domain.Service", layoutEntry("a.kt"))).takeIf { withDefinition },
    )

    private fun stateOf(vararg snapshots: DescriptionSnapshot) = KatachiScreenState(
        phase = ScreenPhase.Ready,
        modules = snapshots.map { it.module },
        snapshots = snapshots.toList(),
    )

    private fun rowOf(module: KatachiModule) = ModuleTemplate(module, service)

    private fun menuOf(state: KatachiScreenState, module: KatachiModule): List<KatachiIntent> {
        val body = (uiStateOf(state, JapaneseKatachiStrings, DEFINITION_INSTANT).body as BodyUi.Listing).list
        val row = body.items.filterIsInstance<ListItemUi.Row>().first { it.row.id.module == module.id }.row
        return row.menu.map { it.intent }
    }

    private class Recording(private val base: FakeIdeEffects = FakeIdeEffects()) : IdeEffects by base {
        val opened: MutableList<Triple<String, String, DeclarationSiteRef?>> = Collections.synchronizedList(mutableListOf())

        override suspend fun openDeclaration(module: KatachiModule, roleName: String, site: DeclarationSiteRef?) {
            opened += Triple(module.gradlePath, roleName, site)
        }
    }

    private fun handle(state: KatachiScreenState, intent: KatachiIntent, effects: Recording): Boolean {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val handler = IdeIntentHandler(scope, FakeGradleTaskRunner(FakeFileSystem()) { _, _ -> FakeRun() }, FakeFileSystem(), effects, { state }, {})
            return handler.handle(intent)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `定義モデルがあるモジュールの行には定義を開くが出る`() {
        val menu = menuOf(stateOf(snapshot(definitionModule, withDefinition = true)), definitionModule)

        assertTrue(menu.toString(), menu.any { it is KatachiIntent.OpenDefinition })
    }

    @Test
    fun `定義モデルが無い 0_2 系のモジュールの行には定義を開くを出さない`() {
        val menu = menuOf(stateOf(snapshot(legacyModule, withDefinition = false)), legacyModule)

        assertTrue(menu.toString(), menu.none { it is KatachiIntent.OpenDefinition })
    }

    @Test
    fun `モジュールが混在するときは定義モデルのあるモジュールの行にだけ出る`() {
        val state = stateOf(snapshot(definitionModule, withDefinition = true), snapshot(legacyModule, withDefinition = false))

        assertTrue(menuOf(state, definitionModule).any { it is KatachiIntent.OpenDefinition })
        assertTrue(menuOf(state, legacyModule).none { it is KatachiIntent.OpenDefinition })
    }

    @Test
    fun `canOpenDefinition は別モジュールの定義を自分のものとして見ない`() {
        val state = stateOf(snapshot(definitionModule, withDefinition = true))

        assertTrue(canOpenDefinition(rowOf(definitionModule), state.snapshots))
        assertFalse(canOpenDefinition(rowOf(legacyModule), state.snapshots))
    }

    @Test
    fun `定義を開くは Role の宣言位置を IDE に渡す`() = runBlocking {
        val state = stateOf(snapshot(definitionModule, withDefinition = true))
        val effects = Recording()

        val handled = handle(state, KatachiIntent.OpenDefinition(TemplateId(definitionModule.id, "domain.Service")), effects)

        assertTrue(handled)
        assertEquals(listOf(Triple(":arch", "domain.Service", DeclarationSiteRef("ServiceRole.kt", 1))), effects.opened.toList())
    }

    @Test
    fun `宣言位置が JSON に無い Role は位置なしで渡す`() = runBlocking {
        val noSite = definitionModel(roleModel("domain.Service", layoutEntry("a.kt")).copy(declaredAt = null))
        val state = stateOf(DescriptionSnapshot(definitionModule, "0.3.0", listOf(service), DEFINITION_INSTANT, noSite))
        val effects = Recording()

        handle(state, KatachiIntent.OpenDefinition(TemplateId(definitionModule.id, "domain.Service")), effects)

        assertEquals(listOf(Triple(":arch", "domain.Service", null)), effects.opened.toList())
        assertNull(roleDeclarationOf(rowOf(definitionModule), state.snapshots))
    }

    @Test
    fun `定義モデルが無い行への定義を開くは IDE に何も頼まない`() = runBlocking {
        val state = stateOf(snapshot(legacyModule, withDefinition = false))
        val effects = Recording()

        val handled = handle(state, KatachiIntent.OpenDefinition(TemplateId(legacyModule.id, "domain.Service")), effects)

        assertTrue(handled)
        assertTrue(effects.opened.isEmpty())
    }
}
