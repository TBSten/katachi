package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.presentation.rules.shortDirectory
import me.tbsten.katachi.intellij.testing.ARCHITECTURE_SITE
import me.tbsten.katachi.intellij.testing.noRoleSnapshot
import me.tbsten.katachi.intellij.testing.rolesFor

/**
 * The world of the no-role notification's platform tests: real files under [root], the real editors and
 * `EditorNotificationsImpl`, a fake definition service whose answers come from the real matcher over [snapshot]
 * (so a file nobody holds has the nearby roles the plugin would give it), the declaration sites written as real files.
 */
internal abstract class NoRoleTestBase : EditorNotificationTestBase() {
    protected val snapshot: DefinitionSnapshot = noRoleSnapshot()

    protected val fake: FakeDefinitionModelService
        get() = definitions as? FakeDefinitionModelService ?: throw AssertionError("the definition service is not the fake")

    override fun setUp() {
        super.setUp()
        // The template side stays out of the way (no service, no Gradle) unless a test installs templates: its load
        // finishing at a random moment redraws everything, which the platform's wait (`completeAsyncTasks`) does not survive.
        synced = SyncedProject.NotSynced
    }

    override fun tearDown() {
        try {
            // The template side loads in the background when a file is opened; the temp directory goes only after it wrote.
            if (servicesCreated.get() > 0) settle()
        } finally {
            super.tearDown()
        }
    }

    protected fun ready(snapshot: DefinitionSnapshot = this.snapshot, freshness: Freshness = Freshness.Fresh) =
        DefinitionState.Ready(listOf(snapshot), freshness)

    /** The definition is read and the fake answers [relative] as the real matcher would. */
    protected fun answer(relative: String, snapshot: DefinitionSnapshot = this.snapshot) {
        fake.answer(path(relative), rolesFor(relative, snapshot))
    }

    /** Writes the files the roles and `architecture { }` are declared in, and tells the fake where they are. */
    protected fun declareSites(): Map<String, VirtualFile> {
        val files = listOf("UseCaseRole.kt", "ModelRole.kt", ARCHITECTURE_SITE.fileName).associateWith { write("arch/$it", "// $it\n") }
        for ((name, file) in files) {
            val line = if (name == ARCHITECTURE_SITE.fileName) ARCHITECTURE_SITE.line else 1
            fake.resolveWith(DeclarationSiteRef(name, line), ResolvedSite.Found(file, line))
        }
        return files
    }

    /** The label text a panel for [relative] has, from the decision-free pieces: the message, the stale note, the nearest role. */
    protected fun expectedText(relative: String, untracked: Boolean = false, stale: Boolean = false): String {
        val main = KatachiBundle.message(if (untracked) "notification.rules.noRole.untracked" else "notification.rules.noRole")
        val note = if (stale) (if (main.last().code < 128) " " else "") + KatachiBundle.message("notification.rules.noRole.stale") else ""
        val nearest = rolesFor(relative, snapshot).nearby.firstOrNull() ?: return main + note
        val second = KatachiBundle.message("notification.rules.noRole.nearby", nearest.role.qualifiedName, shortDirectory(nearest.directory))
        return "<html>${StringUtil.escapeXmlEntities(main + note)}<br>${StringUtil.escapeXmlEntities(second)}</html>"
    }

    /** Waits (pumping the EDT) until the no-role panel of [editor] says [expected]; `null` waits for no panel. */
    protected fun awaitNoRoleText(what: String, editor: FileEditor, expected: String?) {
        PlatformTestUtil.waitWithEventsDispatching({ "$what: expected $expected but the panel says ${peekNoRolePanel(editor)?.text}" }, { peekNoRolePanel(editor)?.text == expected }, 10)
    }

    /** Lets [millis] pass with the EDT running, for "nothing happens" checks. */
    protected fun pump(millis: Long) {
        val until = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < until) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(25)
        }
    }

    /** Opens a file that exists with [relative], its answer from the matcher. */
    protected fun openAnswered(relative: String, text: String = ""): FileEditor {
        answer(relative)
        return open(relative, text)
    }
}
