package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.TestActionEvent
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.model.FileRoles
import com.intellij.openapi.application.ApplicationManager
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.ide.rules.RuleEffects
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent

/** [RuleEffects] that does nothing but remember what was asked, for the tests of the no-role notification. */
internal class FakeRuleEffects : RuleEffects {
    val opened = CopyOnWriteArrayList<ResolvedSite.Found>()
    val copied = CopyOnWriteArrayList<String>()
    val settingsOpened = AtomicInteger(0)
    val refreshes = AtomicInteger(0)

    override fun open(site: ResolvedSite.Found) {
        opened += site
    }

    override fun copy(text: String) {
        copied += text
    }

    override fun refreshDefinition() {
        refreshes.incrementAndGet()
    }

    override fun openSettings() {
        settingsOpened.incrementAndGet()
    }
}

/** A popup the provider was asked to open: under which link, with which rows. */
internal class ShownPopup(val under: JComponent, val group: DefaultActionGroup) {
    /** The rows' texts, a separator as `---`. */
    val rows: List<String> get() = group.getChildren(null).map { if (it is Separator) "---" else it.templatePresentation.text }

    /** Whether the row [text] can be pressed now, as the popup would ask. */
    fun isEnabled(text: String): Boolean {
        val action = row(text)
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabled
    }

    /** Presses the row [text] as the popup does: it must be enabled first. */
    fun press(text: String) {
        val action = row(text)
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        check(event.presentation.isEnabled) { "The row '$text' cannot be pressed" }
        action.actionPerformed(event)
    }

    private fun row(text: String): AnAction =
        group.getChildren(null).singleOrNull { it.templatePresentation.text == text } ?: throw AssertionError("No row '$text' in $rows")
}

/** A definition service whose questions about a file fail, as an SDK that behaves differently from the one built against would. */
internal class ThrowingDefinitions(private val delegate: DefinitionModelService) :
    DefinitionModelService by delegate {
    override fun forFile(file: VirtualFile): FileRoles? = throw IllegalStateException("forFile failed (a test failure)")
}

/** Records on which thread each question was asked: `true` is the EDT. */
internal class RecordingDefinitions(private val delegate: DefinitionModelService) :
    DefinitionModelService by delegate {
    val threads = CopyOnWriteArrayList<Boolean>()

    private fun note() {
        threads += ApplicationManager.getApplication().isDispatchThread
    }

    override fun forFile(file: VirtualFile): FileRoles? {
        note()
        return delegate.forFile(file)
    }

    override fun resolve(module: ModuleId, site: DeclarationSiteRef): ResolvedSite {
        note()
        return delegate.resolve(module, site)
    }
}
