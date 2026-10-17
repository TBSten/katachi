package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import me.tbsten.katachi.intellij.ide.definition.RefreshReason
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.declarationModel
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.templateRef
import me.tbsten.katachi.intellij.testing.withTemplates
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/**
 * The menu through sequences of what a user does around it: open the file, type, save, have the definition refreshed or go
 * stale, press ◆, choose a row, let the check finish. After every step the menu must agree with a model of the world kept
 * apart from the gutter's own rules: what each choice called, whether the check is running, whether Refresh is first.
 * `-Dkatachi.gutter.sequences=N` runs N sequences.
 */
internal class GutterMenuSequenceTest : GutterTestBase() {
    private val fileName = "HealthService.kt"
    private val legacy = "src/main/kotlin/com/example/service/LegacyStatusService.kt"
    private val base = roleModel("domain.Service", layoutEntry(GutterFixtures.SERVICE_PATH, matchedFiles = listOf(GutterFixtures.SERVICE_PATH, legacy)))
    private val service = base.withTemplates(templateRef(base, "service"))
    private val moduleId = module(":arch").id
    private val templateId = TemplateId(moduleId, "domain.Service.service")
    private lateinit var root: Path
    private lateinit var sibling: Path
    private lateinit var runCheck: RecordingAction
    private lateinit var refresh: RecordingAction

    override fun setUp() {
        super.setUp()
        root = Files.createTempDirectory("katachi-gutter-sequence").toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
        sibling = root.resolve(legacy)
        Files.createDirectories(sibling.parent)
        Files.writeString(sibling, "class LegacyStatusService\n")
        runCheck = RecordingAction("run") { violations.runCheck(moduleId) }
        refresh = RecordingAction("refresh") { definitions.refresh(RefreshReason.UserAction) }
        actions[GutterMenu.RUN_CHECK] = runCheck
        actions[GutterMenu.REFRESH] = refresh
    }

    override fun tearDown() {
        try {
            FileUtil.delete(root.toFile())
        } finally {
            super.tearDown()
        }
    }

    /** What the test knows about the world, apart from the menu. */
    private class World {
        var stale = false
        var unsaved = false
        /** The file was written (saved with edits) after the definition was made; a refresh makes a newer definition. */
        var savedSinceRefresh = false
        var running = false
        var runs = 0
        var refreshes = 0
        var dialogs = 0
        /** How many times [ServiceRole.kt] was opened from the menu. */
        var sites = 0
        var refreshBase = 0
    }

    private fun readDefinition(world: World) {
        val explained = roleFileDeclarations(
            service,
            fileDeclarations(
                GutterFixtures.SERVICE_PATH,
                declarationModel(SourceDeclarationKind.Class, "HealthService", 3, rules = listOf(GutterFixtures.SERVICE_RULE)),
            ),
        )
        definitions.emit(DefinitionState.Loading(definitions.state.value as? DefinitionState.Ready))
        definitions.answer(lightPath(fileName), fileRoles(GutterFixtures.SERVICE_PATH, service, projectRoot = root))
        definitions.explain(lightPath(fileName), listOf(explained))
        definitions.emit(DefinitionState.Ready(listOf(refreshedSnapshot()), Freshness.Fresh))
        world.stale = false
        world.savedSinceRefresh = false
    }

    private enum class Op(val redraws: Boolean) {
        Type(true), Save(true), ReadDefinition(true), GoStale(true),
        PressRun(false), FinishCheck(false), PressRefresh(false), PressNew(false), PressSibling(false), PressAllowedBy(false),
    }

    private fun perform(op: Op, world: World) {
        val menu = if (op.redraws) null else menus().single()
        when (op) {
            Op.Type -> { typeAtStart("// a note\n"); world.unsaved = true }
            Op.Save -> { if (world.unsaved) world.savedSinceRefresh = true; saveAll(); world.unsaved = false }
            Op.ReadDefinition -> readDefinition(world)
            Op.GoStale -> {
                definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Stale(DEFINITION_INSTANT, StaleReason.DefinitionChanged)))
                world.stale = true
            }
            Op.PressRun -> {
                choose(menu!!.rows().first { it.templatePresentation.text.startsWith("▶") })
                if (!world.running) { world.runs++; world.running = true }
            }
            Op.FinishCheck -> { violations.finishCheck(moduleId); world.running = false }
            Op.PressRefresh -> { choose(menu!!.row("⟳ Reload Definition")); world.refreshes++ }
            Op.PressNew -> { choose(menu!!.row("+ New domain.Service from template…")); world.dialogs++ }
            Op.PressSibling -> {
                choose(menu!!.row("LegacyStatusService.kt"))
                assertEquals(sibling, java.nio.file.Paths.get(FileEditorManager.getInstance(project).selectedFiles.single().path))
            }
            Op.PressAllowedBy -> {
                choose(menu!!.row("◆ Allowed by"))
                world.sites++
                val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
                assertEquals(roleFile, editor.virtualFile)
                assertEquals(79, editor.caretModel.logicalPosition.line)
            }
        }
        if (op.redraws) redrawn()
    }

    private fun check(world: World, where: String) {
        val menu = menus().single()
        val shown = menu.shown()
        val needsRefreshFirst = world.stale || world.unsaved || world.savedSinceRefresh
        assertEquals("$where: Refresh is first only when the answer may be old\n$shown", needsRefreshFirst, shown.first() == "⟳ Reload Definition")
        assertEquals("$where: exactly one Refresh\n$shown", 1, shown.count { it == "⟳ Reload Definition" })
        val run = shown.single { it.contains("▶") }
        assertEquals("$where: checking while the check runs\n$shown", world.running, run == "(disabled) ▶ Checking…")
        if (!world.running) assertEquals("$where\n$shown", "▶ Run the check (:arch:test)", run)
        assertEquals("$where: run-check calls", world.runs, runCheck.events.size)
        assertEquals("$where: refresh calls", world.refreshes, refresh.events.size)
        assertEquals("$where: refresh reached the service", world.refreshes, definitions.refreshes.size - world.refreshBase)
        assertEquals("$where: dialogs", world.dialogs, effects.dialogs.size)
        effects.dialogs.lastOrNull()?.let {
            assertEquals("$where", templateId, it.initialTemplate)
            assertEquals("$where", EntryOrigin.SiblingOf(root.resolve(GutterFixtures.SERVICE_PATH)), it.origin)
        }
        assertEquals("$where: no balloon for a file that is there", emptyList<String>(), balloons)
    }

    private val roleFile by lazy {
        myFixture.addFileToProject("ServiceRole.kt", (1..100).joinToString("\n") { "// line $it" }).virtualFile
    }

    fun `test 開く 編集 保存 再読み込み 古くなる ◆ を押す 行を選ぶ 検査が終わる の列を流しても メニューは世界の模型と食い違わない`() {
        val sequences = System.getProperty("katachi.gutter.sequences")?.toInt() ?: 12
        definitions.resolveWith(GutterFixtures.SERVICE_RULE.declaredAt!!, ResolvedSite.Found(roleFile, 80))
        definitions.resolveWith(DeclarationSiteRef("ServiceRole.kt", 1), ResolvedSite.Found(roleFile, 1))
        repeat(sequences) { index ->
            val sequenceSeed = 20261005L + index
            val random = Random(sequenceSeed)
            val world = World().also { it.refreshBase = definitions.refreshes.size }
            runCheck.events.clear()
            refresh.events.clear()
            effects.dialogs.clear()
            violations.finishCheck(moduleId)
            myFixture.configureByText(fileName, GutterFixtures.HEALTH_SERVICE_KT)
            saveAll()
            readDefinition(world)
            redrawn()
            val log = mutableListOf("open")
            repeat(10) {
                val op = Op.entries[random.nextInt(Op.entries.size)]
                log += op.name
                perform(op, world)
                check(world, "seed=$sequenceSeed ${log.joinToString(" → ")}")
            }
        }
    }
}
