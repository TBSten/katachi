package me.tbsten.katachi.intellij.ide.entry

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiIdeTestBase
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.Highlight
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel
import java.util.concurrent.CancellationException

/**
 * [View template] of an editor notification over the IDE (C2): the highlight reaches the project
 * service's ViewModel, which outlives the tool window's content, and then the tool window comes
 * up. The activation is swapped for a recorder or a thrower; the real one runs once headless.
 */
internal class RevealTemplateInToolWindowTest : KatachiIdeTestBase() {
    private val balloons = mutableListOf<String>()
    private val activated = mutableListOf<Project>()
    private lateinit var viewModel: KatachiToolWindowViewModel
    private lateinit var template: TemplateId

    override fun setUp() {
        super.setUp()
        viewModel = installService()
        template = TemplateId(ModuleId(root, ":arch-a"), "domain.UseCase")
    }

    private fun effects(activate: (Project) -> Unit = { activated += it }) =
        EntryEffectsImpl(project, notify = { balloons += it }, activateToolWindow = activate)

    // covers: 論点17
    fun `test 強調をサービスの ViewModel に渡してからツールウィンドウを1回開く`() {
        var highlightWhenActivated: Highlight? = null

        effects(activate = {
            highlightWhenActivated = viewModel.state.value.view.highlight
            activated += it
        }).revealTemplateInToolWindow(template)

        assertEquals(listOf(project), activated)
        // Dispatched before the activation: a tool window created by it reads the highlight already.
        assertEquals(template, highlightWhenActivated?.templateId)
        assertEquals(emptyList<String>(), balloons)
    }

    // covers: 論点17
    fun `test ツールウィンドウを開く呼び出しが例外を投げても操作は失敗せず balloon で知らせ強調は残る`() {
        effects(activate = { throw IllegalStateException("no tool window") }).revealTemplateInToolWindow(template)

        assertEquals(listOf(KatachiBundle.message("entry.revealFailed", "no tool window")), balloons)
        assertEquals(template, viewModel.state.value.view.highlight?.templateId)
    }

    // covers: 論点17
    fun `test ツールウィンドウを開く呼び出しの ProcessCanceledException と CancellationException は伝わる`() {
        assertThrows(ProcessCanceledException::class.java) {
            effects(activate = { throw ProcessCanceledException() }).revealTemplateInToolWindow(template)
        }
        assertThrows(CancellationException::class.java) {
            effects(activate = { throw CancellationException("cancelled") }).revealTemplateInToolWindow(template)
        }
        assertEquals(emptyList<String>(), balloons)
    }

    // covers: 論点17
    fun `test 本物の ToolWindowManager で開いても例外にも balloon にもならない`() {
        EntryEffectsImpl(project, notify = { balloons += it }).revealTemplateInToolWindow(template)

        assertEquals(emptyList<String>(), balloons)
        assertEquals(template, viewModel.state.value.view.highlight?.templateId)
    }
}
