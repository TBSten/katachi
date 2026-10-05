package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.tbsten.katachi.intellij.data.definition.DefinitionMatcher
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import java.nio.file.Paths

/**
 * The fake keeps the real service's contract, so a feature's test can trust it: no answer without a
 * state that has one, the state's freshness on every answer, every state of a sequence observable.
 */
internal class FakeDefinitionModelServiceTest : BasePlatformTestCase() {
    private val snapshot = definitionSnapshot(definitionModel(roleModel("domain.UseCase", layoutEntry("src/*.kt"))))
    private val module = module(":arch")

    private fun fileOf(path: String): VirtualFile = LightVirtualFile(path.substringAfterLast('/'))

    private fun rolesOf(file: VirtualFile, freshness: Freshness = Freshness.Fresh): FileRoles =
        DefinitionMatcher(snapshot.definition ?: error("no definition")).matchFile("src/A.kt")
            .within(module, Paths.get("/w/repo"), "src/A.kt", freshness)

    fun `test 状態に答えが無いうちは scripted な答えがあっても何も返さない`() {
        val file = fileOf("src/A.kt")
        val service = FakeDefinitionModelService()
        service.answer(Paths.get(file.path), rolesOf(file))

        assertNull(service.forFile(file))
        service.emit(DefinitionState.Loading(previous = null))
        assertNull(service.forFile(file))
        assertEquals(emptyList<Any>(), service.definitions)
    }

    fun `test 答えは Ready のあと Loading と Stale の間も返り freshness は状態のものになる`() {
        val file = fileOf("src/A.kt")
        val service = FakeDefinitionModelService()
        service.answer(Paths.get(file.path), rolesOf(file))
        val states = DefinitionStateScripts.standardCycle(snapshot)

        val seen = mutableListOf<Pair<DefinitionState, Freshness?>>()
        service.play(states) { state -> seen += state to service.forFile(file)?.freshness }

        assertEquals(states, service.history)
        // The answer exists exactly when the state has one, and it wears that state's freshness.
        for ((state, freshness) in seen) assertEquals(state.answer?.freshness, freshness)
        assertTrue(seen.any { (_, freshness) -> freshness is Freshness.Stale })
        assertTrue(seen.any { (state, freshness) -> state is DefinitionState.Loading && freshness != null })
    }

    fun `test 列の状態は step で1つずつ観測でき emit の列は履歴にすべて残る`() {
        val service = FakeDefinitionModelService()
        val states = DefinitionStateScripts.standardCycle(snapshot)
        service.script(*states.drop(1).toTypedArray())

        val observed = mutableListOf(service.state.value)
        while (service.step()) observed += service.state.value

        assertEquals(states, observed)
        assertFalse(service.step())

        val other = FakeDefinitionModelService()
        other.emit(*states.toTypedArray())
        assertEquals(states, other.history)
        assertEquals(states.last(), other.state.value)
    }

    fun `test 呼び出しは記録され definitionOf は答えの定義モジュールを引く`() {
        val service = FakeDefinitionModelService(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        service.ensureLoaded()
        service.ensureLoaded()
        service.refresh(RefreshReason.UserAction)
        service.refresh(RefreshReason.DefinitionChanged)

        assertEquals(2, service.ensureLoadedCalls)
        assertEquals(listOf(RefreshReason.UserAction, RefreshReason.DefinitionChanged), service.refreshes)
        assertSame(snapshot, service.definitionOf(snapshot.module.id))
        assertNull(service.definitionOf(module(":missing").id))
        assertEquals(listOf(snapshot), service.definitions)
    }

    fun `test answer に null を渡すと答えが消えて forFile は null を返す`() {
        val file = fileOf("src/A.kt")
        val service = FakeDefinitionModelService(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))

        service.answer(Paths.get(file.path), null)
        assertNull(service.forFile(file))

        service.answer(Paths.get(file.path), rolesOf(file))
        assertNotNull(service.forFile(file))
        service.answer(Paths.get(file.path), null)
        assertNull(service.forFile(file))
        assertNull(service.forPath(Paths.get(file.path)))
    }

    fun `test resolve は scripted した宣言元を返し無ければ Unresolved`() {
        val service = FakeDefinitionModelService()
        val file = fileOf("roles/UseCaseRole.kt")
        val site = DeclarationSiteRef("UseCaseRole.kt", 10)

        assertTrue(service.resolve(module.id, site) is ResolvedSite.Unresolved)
        service.resolveWith(site, ResolvedSite.Found(file, 10))
        assertEquals(ResolvedSite.Found(file, 10), service.resolve(module.id, site))
    }

    fun `test fileDeclarationsOf も状態の freshness を付けて返す`() {
        val file = fileOf("src/A.kt")
        val roles = rolesOf(file)
        val service = FakeDefinitionModelService(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        val declarations = me.tbsten.katachi.intellij.model.RoleFileDeclarations(
            role = roles.matches.single().role,
            constraint = me.tbsten.katachi.intellij.testing.constraintModel(),
            declarations = me.tbsten.katachi.intellij.model.FileDeclarationsModel("src/A.kt", true, emptyList(), emptyList(), null),
            freshness = Freshness.Fresh,
        )
        service.explain(Paths.get(file.path), listOf(declarations))

        assertEquals(listOf(Freshness.Fresh), service.fileDeclarationsOf(file).map { it.freshness })
        val stale = Freshness.Stale(java.time.Instant.EPOCH, StaleReason.DefinitionChanged)
        service.emit(DefinitionState.Ready(listOf(snapshot), stale))
        assertEquals(listOf<Freshness>(stale), service.fileDeclarationsOf(file).map { it.freshness })
        service.emit(DefinitionState.NotLoaded)
        assertEquals(emptyList<Any>(), service.fileDeclarationsOf(file))
    }
}
