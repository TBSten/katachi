package me.tbsten.katachi.intellij.ide.spike

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestActionEvent
import me.tbsten.katachi.intellij.ide.KatachiIdeTestBase
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** S2 (d): a dynamic ActionGroup in NewGroup, driven by TestActionEvent with an IdeView. */
internal class NewMenuSpikeTest : KatachiIdeTestBase() {
    private fun directory(relative: String): PsiDirectory {
        val path = root.resolve(relative)
        Files.createDirectories(path)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("not in VFS")
        return PsiManager.getInstance(project).findDirectory(file) ?: throw AssertionError("no PsiDirectory for $path")
    }

    private fun contextOf(vararg directories: PsiDirectory): DataContext {
        val view = object : IdeView {
            override fun getDirectories(): Array<PsiDirectory> = arrayOf(*directories)
            override fun getOrChooseDirectory(): PsiDirectory? = null
        }
        return SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(LangDataKeys.IDE_VIEW, view).build()
    }

    private fun addToNewGroup(group: AnAction) {
        val newGroup = ActionManager.getInstance().getAction("NewGroup") as DefaultActionGroup
        newGroup.add(group)
        Disposer.register(testRootDisposable) { newGroup.remove(group) }
    }

    // covers: 論点2
    fun `test d TestActionEvent と IDE_VIEW で update と getChildren を呼べて複数のディレクトリが渡る`() {
        val group = SpikeNewGroup()
        addToNewGroup(group)
        val a = directory("feature/src/main/kotlin/com/example/a")
        val b = directory("feature/src/main/kotlin/com/example/b")
        val event = TestActionEvent.createTestEvent(group, contextOf(a, b))

        group.update(event)
        val children = group.getChildren(event)

        spike("d.edt.visible", event.presentation.isEnabledAndVisible)
        spike("d.edt.children", children.map { it.templatePresentation.text })
        assertTrue(event.presentation.isEnabledAndVisible)
        assertEquals(listOf("a", "b"), children.map { it.templatePresentation.text })
    }

    // covers: 論点2
    fun `test d BGT の読み取りロックのもとで update と getChildren を呼べる`() {
        val group = SpikeNewGroup()
        val a = directory("feature/src/main/kotlin/com/example/a")
        val event = TestActionEvent.createTestEvent(group, contextOf(a))

        val names = onBgtWithReadLock {
            group.update(event)
            group.getChildren(event).map { it.templatePresentation.text }
        }

        spike("d.bgt.updateThreads", group.updateOnEdt)
        assertEquals(listOf(false), group.updateOnEdt.distinct())
        assertTrue(event.presentation.isEnabledAndVisible)
        assertEquals(listOf("a"), names)
    }

    // covers: 論点2
    fun `test d ディレクトリが無ければ隠れる`() {
        val group = SpikeNewGroup()
        val event = TestActionEvent.createTestEvent(group, contextOf())

        group.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    // covers: 論点2
    fun `test d NewGroup を本物の展開にかけると popup のグループとして出てくる`() {
        val group = SpikeNewGroup().apply { templatePresentation.isPopupGroup = true }
        addToNewGroup(group)
        val a = directory("feature/src/main/kotlin/com/example/a")
        val newGroup = ActionManager.getInstance().getAction("NewGroup") as ActionGroup

        var thrown: Throwable? = null
        var expanded: List<AnAction> = emptyList()
        var ours: List<AnAction> = emptyList()
        val errors = loggedErrorsOf {
            try {
                expanded = Utils.expandActionGroup(newGroup, PresentationFactory(), contextOf(a), ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP)
                ours = Utils.expandActionGroup(group, PresentationFactory(), contextOf(a), ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP)
            } catch (e: Throwable) {
                thrown = e
            }
        }
        spike("d.expand.thrown", thrown?.let { "${it.javaClass.name}: ${it.message}" })
        spike("d.expand.errors", errors)
        spike("d.expand.containsOurs", expanded.contains(group))
        spike("d.expand.size", expanded.size)
        spike("d.expand.ours", ours.map { it.templatePresentation.text })
        spike("d.expand.updateOnEdt", group.updateOnEdt)
        assertNull(thrown)
        assertTrue(expanded.contains(group))
        assertEquals(listOf("a"), ours.map { it.templatePresentation.text })
    }

    // covers: 論点2
    fun `test d NewGroup が ActionManager に登録されている`() {
        assertTrue(ActionManager.getInstance().getAction("NewGroup") is DefaultActionGroup)
    }
}

/** One item per directory of the IdeView, named after it. No DynamicActionGroup: it is deprecated in 261 and not needed (run15). */
internal class SpikeNewGroup : ActionGroup("katachi spike", true), DumbAware {
    val updateOnEdt: MutableList<Boolean> = CopyOnWriteArrayList()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        updateOnEdt += ApplicationManager.getApplication().isDispatchThread
        e.presentation.isEnabledAndVisible = directoriesOf(e).isNotEmpty()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
        directoriesOf(e).map { dir -> Leaf(dir.name) }.toTypedArray()

    private fun directoriesOf(e: AnActionEvent?): List<PsiDirectory> = e?.getData(LangDataKeys.IDE_VIEW)?.directories.orEmpty().toList()

    private class Leaf(name: String) : AnAction(name), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = Unit
    }
}
