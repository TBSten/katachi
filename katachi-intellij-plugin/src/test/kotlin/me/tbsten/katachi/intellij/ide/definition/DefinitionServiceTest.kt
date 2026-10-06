package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.RefreshDefinitionAction
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.presentation.ScreenPhase

/** V4 of the definition service: the answers it gives once the definition is read (design 4.5, 4.6). */
internal class DefinitionServiceTest : DefinitionServiceTestBase() {
    fun `test 読み込むと Ready になり forFile が役割と制約を答える`() {
        install()
        assertEquals(DefinitionState.NotLoaded, definition.state.value)
        val ready = loaded()

        assertEquals(Freshness.Fresh, ready.freshness)
        assertEquals(listOf(":arch:katachiInternalDefinitionJson"), gradle.loadRequests.single().taskNames)
        val file = writeFile("src/service/HealthService.kt", "class HealthService\n")
        val roles = definition.forFile(file) ?: throw AssertionError("no answer")
        assertEquals("src/service/HealthService.kt", roles.relativePath)
        assertEquals(MatchOutcome.Matched, roles.outcome)
        assertEquals(listOf("Service"), roles.matches.map { it.role.qualifiedName })
        assertEquals(2, roles.constraints.size)
        // The template list of the tool window comes from the same JSON.
        assertEquals(ScreenPhase.Empty(me.tbsten.katachi.intellij.presentation.EmptyReason.NoTemplates), projectService.viewModel.state.value.phase)
        assertEquals(listOf(DefinitionState.NotLoaded), history.take(1))
    }

    fun `test 当たる役割が無いファイルは近い Role を添えて答える`() {
        install()
        loaded()
        val roles = definition.forPath(root.resolve("src/other/Stray.kt")) ?: throw AssertionError("no answer")
        assertEquals(MatchOutcome.UnexpectedUnder("src/other"), roles.outcome)
        // Both places are one replaced level away with a fitting name; `thrower` is 4 characters from `other`, `service` 7.
        assertEquals(listOf("src/thrower", "src/service"), roles.nearby.map { it.directory })
    }

    fun `test gitTracked の定義では VCS が無視するファイルは IgnoredByVcs になる`() {
        gradle.definitionOf["arch"] = definitionJson("synthetic-definition-explained").replace("\"files\": \"wholeTree\"", "\"files\": \"gitTracked\"")
        install(ignoredByVcs = { it.name == "Ignored.kt" })
        loaded()
        val ignored = writeFile("src/other/Ignored.kt", "")
        val stray = writeFile("src/other/Stray.kt", "")
        assertEquals(MatchOutcome.IgnoredByVcs, definition.forFile(ignored)?.outcome)
        assertEquals(emptyList<Any>(), definition.forFile(ignored)?.nearby)
        assertEquals(MatchOutcome.UnexpectedUnder("src/other"), definition.forFile(stray)?.outcome)
    }

    fun `test katachi 0_2 の同期データでは KatachiTooOld でテンプレートは読める`() {
        synced = syncedWith(":arch-a")
        install()
        definition.ensureLoaded()
        waitState("too old") { it == DefinitionState.Unavailable(DefinitionUnavailableReason.KatachiTooOld, null) }
        projectService.viewModel.waitFor("templates") { it.phase == ScreenPhase.Ready && it.loading == null }
        assertEquals(listOf(":arch-a:katachiInternalTemplatesJson"), gradle.loadRequests.single().taskNames)
        assertNull(definition.forPath(root.resolve("src/service/HealthService.kt")))
    }

    fun `test 定義モジュールが2つで入れ子ならそれぞれ深い方のルートで答える`() {
        synced = SyncedProject.Synced(
            listOf(
                SyncedRoot(
                    root,
                    "project",
                    listOf(":arch", ":inner").map { SyncedModule(it, moduleDirOf(it), setOf(KatachiModule.DEFINITION_JSON_TASK), "0.3.0") },
                ),
            ),
        )
        // :inner's project root is below :arch's.
        gradle.definitionOf["inner"] = definitionJson("synthetic-definition-basic", root.resolve("sub"))
        install()
        loaded()
        assertEquals(2, definition.definitions.size)

        val outer = definition.forPath(root.resolve("src/service/HealthService.kt")) ?: throw AssertionError()
        val inner = definition.forPath(root.resolve("sub/settings.gradle.kts")) ?: throw AssertionError()
        assertEquals(":arch", outer.module.gradlePath)
        assertEquals(":inner", inner.module.gradlePath)
        assertEquals("settings.gradle.kts", inner.relativePath)
        assertEquals(listOf("RootBuild"), inner.matches.map { it.role.qualifiedName })
        assertEquals(":inner", definition.definitionOf(inner.module.id)?.module?.gradlePath)
    }

    fun `test 宣言元を test ソースルートから解決し同名が2つなら解決しない`() {
        install()
        loaded()
        val arch = definition.definitions.single().module
        val rootA = addTestSourceRoot(root.resolve("arch/src/jvmTest/kotlin"))
        writeFile("arch/src/jvmTest/kotlin/roles/ServiceRole.kt", "// role\n")

        val found = definition.resolve(arch.id, DeclarationSiteRef("ServiceRole.kt", 80))
        found as ResolvedSite.Found
        assertEquals("ServiceRole.kt", found.file.name)
        assertEquals(80, found.line)
        assertTrue(definition.resolve(arch.id, DeclarationSiteRef("Nowhere.kt", 1)) is ResolvedSite.Unresolved)

        // A second file of the same name: unresolved once the walk is dropped (after the changes stop for 500 ms).
        writeFile("arch/src/jvmTest/kotlin/other/ServiceRole.kt", "// other\n")
        assertTrue(rootA.isValid)
        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
            { "still resolved: ${definition.resolve(arch.id, DeclarationSiteRef("ServiceRole.kt", 80))}" },
            { definition.resolve(arch.id, DeclarationSiteRef("ServiceRole.kt", 80)) is ResolvedSite.Unresolved },
            10,
        )
    }

    // The line marker and the annotator ask from inside the daemon's read action: a walk that cannot be cancelled would make a key stroke wait for all of it.
    // (The VFS iteration checks too, so this pins the behaviour, not the resolver's own check.)
    fun `test 宣言元の探索は ファイルごとに取り消しを確かめ 取り消されたら止まって 半端な答えを残さない`() {
        install()
        loaded()
        val arch = definition.definitions.single().module
        addTestSourceRoot(root.resolve("arch/src/jvmTest/kotlin"))
        repeat(FILES) { writeFile("arch/src/jvmTest/kotlin/roles/Role$it.kt", "// role\n") }
        val site = DeclarationSiteRef("Role0.kt", 1)
        val progress = com.intellij.openapi.progress.ProgressManager.getInstance() as com.intellij.openapi.progress.impl.ProgressManagerImpl

        // Every check of the walk is seen by a hook: one for each file at least.
        projectService.siteResolver.invalidateRoots()
        var checks = 0
        val counting = com.intellij.openapi.progress.impl.CoreProgressManager.CheckCanceledHook {
            checks++
            false
        }
        progress.addCheckCanceledHook(counting)
        try {
            assertTrue(definition.resolve(arch.id, site) is ResolvedSite.Found)
        } finally {
            progress.removeCheckCanceledHook(counting)
        }
        assertTrue("one check for each of the $FILES files: $checks", checks >= FILES)

        // A cancel in the middle of the walk (a write is waiting): the walk stops, and the next ask walks again and answers.
        projectService.siteResolver.invalidateRoots()
        var seen = 0
        val cancelling = com.intellij.openapi.progress.impl.CoreProgressManager.CheckCanceledHook {
            if (++seen == FILES / 2) throw com.intellij.openapi.progress.ProcessCanceledException()
            false
        }
        progress.addCheckCanceledHook(cancelling)
        try {
            assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) { definition.resolve(arch.id, site) }
        } finally {
            progress.removeCheckCanceledHook(cancelling)
        }
        assertTrue("the next ask walks again and answers", definition.resolve(arch.id, site) is ResolvedSite.Found)
    }

    fun `test 定義の test ソースセット以外のルートは宣言元の候補にも自動再読み込みの契機にも入れない`() {
        KatachiSettings.getInstance(project).autoReloadOnSave = true
        install(autoReloadDelayMillis = 200)
        loaded()
        val arch = definition.definitions.single().module
        addTestSourceRoot(root.resolve("arch/src/integrationTest/kotlin"))
        addTestSourceRoot(root.resolve("arch/src/jvmTest/kotlin"))
        writeFile("arch/src/integrationTest/kotlin/ServiceRole.kt", "// integration\n")
        writeFile("arch/src/jvmTest/kotlin/ServiceRole.kt", "// role\n")

        // Not "two files named ServiceRole.kt": the integration test's one is not the definition's.
        val found = definition.resolve(arch.id, DeclarationSiteRef("ServiceRole.kt", 1)) as ResolvedSite.Found
        assertEquals("jvmTest", found.file.parent.parent.name)

        // The jvmTest save reloads; one in integrationTest does not.
        val until = System.currentTimeMillis() + 1_200
        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching({ "idle" }, { System.currentTimeMillis() >= until }, 10)
        assertEquals(2, gradle.loadRequests.size)
        writeFile("arch/src/integrationTest/kotlin/ServiceRole.kt", "// integration v2\n")
        val until2 = System.currentTimeMillis() + 1_000
        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching({ "idle" }, { System.currentTimeMillis() >= until2 }, 10)
        assertEquals(2, gradle.loadRequests.size)
    }

    fun `test IDE がテストソースルートを知らなければ src_test_kotlin で解決する`() {
        install()
        loaded()
        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// role\n")
        val arch = definition.definitions.single().module
        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
            { "unresolved" },
            { definition.resolve(arch.id, DeclarationSiteRef("ServiceRole.kt", 3)) is ResolvedSite.Found },
            10,
        )
    }

    fun `test fileDeclarationsOf は説明のあるファイルに役割ごとの説明を返し古くなっても前の答えを返す`() {
        install()
        loaded()
        val health = writeFile("src/service/HealthService.kt", "class HealthService\n")
        val explained = definition.fileDeclarationsOf(health)
        assertEquals(listOf("Service"), explained.map { it.role.qualifiedName })
        assertEquals(DeclarationVerdict.Allowed, explained.single().declarations.declarations.single().verdict)
        assertEquals(emptyList<Any>(), definition.fileDeclarationsOf(writeFile("src/service/README.md", "")))

        // A save of the definition: the answer stays, marked stale.
        KatachiSettings.getInstance(project).autoReloadOnSave = false
        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v2\n")
        waitState("stale") { (it as? DefinitionState.Ready)?.freshness is Freshness.Stale }
        val stale = definition.fileDeclarationsOf(health)
        assertEquals(explained.map { it.declarations }, stale.map { it.declarations })
        assertTrue(stale.single().freshness is Freshness.Stale)
        assertTrue(definition.forFile(health)?.freshness is Freshness.Stale)
    }

    fun `test dumb mode でも forFile と resolve が答える`() {
        install()
        loaded()
        val file = writeFile("src/service/HealthService.kt", "class HealthService\n")
        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// role\n")
        val arch = definition.definitions.single().module
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            assertEquals(listOf("Service"), definition.forFile(file)?.matches?.map { it.role.qualifiedName })
            assertTrue(definition.resolve(arch.id, DeclarationSiteRef("ServiceRole.kt", 1)) is ResolvedSite.Found)
        }
    }

    fun `test 定義モジュールの無いプロジェクトでは DefinitionPorts はサービスを作らない`() {
        val created = mutableListOf<String>()
        val ports = DefinitionPorts(
            serviceExists = { false },
            hasDefinitionModule = { false },
            createDefinition = { created += "definition"; throw AssertionError() },
            createViolations = { created += "violations"; throw AssertionError() },
        )
        assertNull(ports.service())
        assertNull(ports.violations())
        assertEquals(emptyList<String>(), created)
    }

    fun `test 定義モジュールのあるプロジェクトでは DefinitionPorts が両方のサービスを実際に返す`() {
        install()
        val ports = DefinitionPorts(
            serviceExists = { true },
            hasDefinitionModule = { true },
            createDefinition = { definition },
            createViolations = { me.tbsten.katachi.intellij.ide.sarif.FakeViolationsService() },
        )
        assertSame(definition, ports.service())
        assertNotNull(ports.violations())
    }

    fun `test katachi_RefreshDefinition は何も読んでいない状態から実行しても読み込みを始める`() {
        install()
        project.replaceService(KatachiDefinitionService::class.java, definition, testRootDisposable)
        assertEquals(DefinitionState.NotLoaded, definition.state.value)

        val action = ActionManager.getInstance().getAction(RefreshDefinitionAction.ID)
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))

        waitState("loaded") { it is DefinitionState.Ready && !projectService.viewModel.isLoadRunning }
        assertEquals(1, gradle.loadRequests.size)
    }

    private companion object {
        const val FILES = 12
    }
}
