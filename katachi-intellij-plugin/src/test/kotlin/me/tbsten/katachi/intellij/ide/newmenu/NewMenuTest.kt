package me.tbsten.katachi.intellij.ide.newmenu

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.TestActionEvent
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.PlacementUnavailableReason
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.LoadAnswer
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.testing.indexOf
import me.tbsten.katachi.intellij.testing.patternTemplate
import me.tbsten.katachi.intellij.testing.snapshotOf

/** New › katachi as the IDE drives it: visibility, structure, item names, the threads' rules and the leaf's click. */
internal class NewMenuTest : NewMenuTestBase() {
    private val example get() = "feature/src/main/com/example"

    // covers: 論点12
    fun `test 当たるディレクトリでグループと役割の構造と項目名が出て関係のないディレクトリでは出ない`() {
        installReadyMenu(screen, viewModel, repository)

        val menu = menuOn(directory(example))

        assertEquals(
            listOf(
                "feature",
                "  Screen — feature/<feature>/ui/<screen>Screen.kt",
                "  ViewModel — feature/<feature>/<screen>ViewModel.kt",
            ),
            menu,
        )
        assertEquals(emptyList<String>(), menuOn(directory("docs/other")))
    }

    // covers: 論点1
    fun `test 利用者の例で深さごとに項目名の残りのパスが変わる`() {
        installReadyMenu(screen)

        assertEquals(listOf("feature", "  Screen — feature/<feature>/ui/<screen>Screen.kt"), menuOn(directory(example)))
        assertEquals(listOf("feature", "  Screen — ui/<screen>Screen.kt"), menuOn(directory("$example/feature/profile")))
    }

    // covers: 論点7
    fun `test 定義が2つ以上のときだけ最上段に定義の段が出る`() {
        installMenuService { indexOf(snapshotOf(moduleA, listOf(screen)), snapshotOf(moduleB, listOf(patternTemplate("feature.Other", "feature/src/main/com/example/\${x}Other.kt")))) }
        synced = syncedWith(":arch-a", ":arch-b")
        loadAndSettle()

        assertEquals(
            listOf(":arch-a", "  feature", "    Screen — feature/<feature>/ui/<screen>Screen.kt", ":arch-b", "  feature", "    Other — <x>Other.kt"),
            menuOn(directory(example)),
        )
    }

    // covers: 論点3
    fun `test 未同期と JSON の失敗では読み込み後に隠れる`() {
        synced = SyncedProject.NotSynced
        installReadyMenu(screen)
        assertEquals(PlacementIndexState.Unavailable(PlacementUnavailableReason.NotSynced), service.placementIndex.value)
        assertEquals(emptyList<String>(), menuOn(directory(example)))
    }

    // covers: 論点3
    fun `test JSON の生成に失敗したときは隠れる`() {
        installMenuService { indexOf(snapshotOf(moduleA, listOf(screen))) }
        gradle.loads += LoadAnswer(fails = true)
        loadAndSettle()

        waitForIndex("failed") { it is PlacementIndexState.Unavailable }
        assertEquals(emptyList<String>(), menuOn(directory(example)))
    }

    // covers: 論点3
    fun `test dumb mode でも出る`() {
        installReadyMenu(screen)
        val directory = directory(example)

        val menu = DumbModeTestUtils.computeInDumbModeSynchronously(project) { menuOn(directory) }

        assertEquals(2, menu.size)
    }

    // covers: 論点23
    fun `test 複数のディレクトリは当たりの和集合で、同じテンプレートは1回で起点は最初に当たったディレクトリ`() {
        installReadyMenu(screen, repository)
        val home = directory("$example/feature/home")
        val profile = directory("$example/feature/profile")
        val domain = directory("domain/src/main/com/example")
        val menu = menuOn(home, profile, domain)
        assertEquals(1, menu.count { it.contains("Screen") })
        assertEquals(1, menu.count { it.contains("Repository") })

        val event = eventOn(home, profile)
        group.update(event)
        val leaf = leavesOf(group.getChildren(event)).single()
        leaf.actionPerformed(TestActionEvent.createTestEvent(leaf))
        val reversed = eventOn(profile, home)
        group.update(reversed)
        val reversedLeaf = leavesOf(group.getChildren(reversed)).single()
        reversedLeaf.actionPerformed(TestActionEvent.createTestEvent(reversedLeaf))

        val id = TemplateId(ModuleId(root, ":arch-a"), "feature.Screen")
        assertEquals(
            listOf(
                GenerateDialogRequest(EntryOrigin.NewMenuDirectory(pathOf(home)), id, mapOf("feature" to "home")),
                GenerateDialogRequest(EntryOrigin.NewMenuDirectory(pathOf(profile)), id, mapOf("feature" to "profile")),
            ),
            entryEffects.dialogs.toList(),
        )
    }

    // covers: 論点2
    fun `test ディレクトリが0個または IDE_VIEW が無いときは隠れる`() {
        installReadyMenu(screen)

        assertFalse(visibleAfterUpdate(eventOn()))
        assertFalse(visibleAfterUpdate(eventWith(view = null)))
    }

    // covers: 論点11
    fun `test 未導入なら判定の後は読み込み中の項目も出さず Gradle も走らせない`() {
        synced = SyncedProject.Synced(listOf(SyncedRoot(root, "project", listOf(SyncedModule(":app", moduleDirOf(":app"), emptySet(), null)))))
        installReadyMenu(screen)
        waitForIndex("not installed") { it is PlacementIndexState.Unavailable }

        assertEquals(emptyList<String>(), menuOn(directory(example)))
        assertTrue(gradle.requests.toString(), gradle.requests.isEmpty())
    }

    // covers: 論点11
    fun `test 利用者の操作なしの読み込みが OFF で未読み込みなら何も出さず Gradle も走らせない`() {
        setAutoLoad(false)
        installReadyMenu(screen)
        waitForIndex("disabled") { it is PlacementIndexState.Unavailable }

        assertEquals(emptyList<String>(), menuOn(directory(example)))
        assertTrue(gradle.requests.toString(), gradle.requests.isEmpty())
    }

    // covers: 論点18
    fun `test 読み込み前は押せない1項目だけで、待たずに返り、読み込みは1回だけ始まる`() {
        installMenuService { indexOf(snapshotOf(moduleA, listOf(screen))) }
        val directory = directory(example)
        val event = eventOn(directory)

        group.update(event)
        val children = group.getChildren(event)
        group.update(eventOn(directory))
        group.update(eventOn(directory))

        assertTrue(event.presentation.isEnabledAndVisible)
        assertEquals(listOf(KatachiBundle.message("newMenu.loading")), outline(children))
        val item = children.single()
        val itemEvent = AnActionEvent.createEvent(DataContext.EMPTY_CONTEXT, item.templatePresentation.clone(), "test", ActionUiKind.NONE, null)
        item.update(itemEvent)
        assertFalse(itemEvent.presentation.isEnabled)
        // update returned without the load: it starts on the EDT, which is busy running this test.
        assertTrue(gradle.requests.toString(), gradle.requests.isEmpty())
        settle()
        assertEquals(1, gradle.loadRequests.size)
        assertEquals(2, outline(group.getChildren(event)).size)
    }

    // covers: 論点4
    fun `test 500 テンプレートでも update と getChildren は 50ms を超えず Gradle を走らせない`() {
        val many = List(500) { patternTemplate("g${it % 10}.T$it", "feature/src/main/com/example/feature/\${feature}/g$it/\${name}T$it.kt") }
        installReadyMenu(*many.toTypedArray())
        val directory = directory("$example/feature/home")
        val requests = gradle.requests.size
        fun once(): Long {
            val event = eventOn(directory)
            val start = System.nanoTime()
            group.update(event)
            assertEquals(500, leavesOf(group.getChildren(event)).size)
            return (System.nanoTime() - start) / 1_000_000
        }
        repeat(5) { once() }

        val median = List(20) { once() }.sorted()[10]

        assertTrue("median ${median}ms", median < 50)
        assertEquals(requests, gradle.requests.size)
    }

    // covers: 論点2
    fun `test SDK の呼び出しが例外を投げても update は失敗せず隠れる`() {
        installReadyMenu(screen)
        val event = eventWith(brokenView(IllegalStateException("the view is gone")))

        assertFalse(visibleAfterUpdate(event))
    }

    // covers: 論点2
    fun `test ProcessCanceledException は握らず伝わる`() {
        installReadyMenu(screen)
        val event = eventWith(brokenView(ProcessCanceledException()))

        try {
            group.update(event)
            fail("the cancellation must reach the platform")
        } catch (expected: ProcessCanceledException) {
            // The platform restarts the update.
        }
    }

    private fun brokenView(failure: Throwable): IdeView = object : IdeView {
        override fun getDirectories(): Array<PsiDirectory> = throw failure
        override fun getOrChooseDirectory(): PsiDirectory? = null
    }

    private fun leavesOf(actions: Array<AnAction>): List<AnAction> =
        actions.flatMap { if (it is ActionGroup) leavesOf(it.getChildren(null)) else listOf(it) }
}
