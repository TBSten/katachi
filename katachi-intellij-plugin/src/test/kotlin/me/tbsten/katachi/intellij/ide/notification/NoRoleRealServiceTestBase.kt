package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.ide.definition.KatachiDefinitionService
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.testing.DefinitionFixtures

/**
 * The world of the no-role tests that run over the real definition service, the real project service and a fake Gradle that
 * writes a real `definition.json`: nothing of the definition side is faked. The definition has the `Service` role in
 * `src/service` (`*.kt`, `*.md`) and the `Thrower` role in `src/thrower`.
 */
internal abstract class NoRoleRealServiceTestBase : NoRoleTestBase() {
    protected lateinit var real: KatachiDefinitionService
    private val explained = "synthetic-definition-explained"

    protected fun definitionJson(name: String = explained): String =
        DefinitionFixtures.definitionText(name).replace("\"projectRoot\": \"/repo\"", "\"projectRoot\": \"$root\"")

    protected fun installReal(json: String = definitionJson(), ignoredByVcs: (VirtualFile) -> Boolean = { false }) {
        synced = syncedWithDefinition(":arch")
        gradle.definitionOf["arch"] = json
        real = KatachiDefinitionService(ports().service(), ignoredByVcs)
        definitions = real
    }

    protected fun awaitState(what: String, condition: (DefinitionState) -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching({ "$what: ${real.state.value}" }, { condition(real.state.value) }, 10)
    }

    protected fun awaitPanel(what: String, editor: FileEditor, present: Boolean) {
        PlatformTestUtil.waitWithEventsDispatching({ "$what: panel ${peekNoRolePanel(editor)?.text}" }, { (peekNoRolePanel(editor) != null) == present }, 10)
    }
}
