package me.tbsten.katachi.intellij

import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The capture candidates in a real IDE (ide-review-screens plan 4.6): a copy of sample/kmp is opened and imported, the
 * `feature/` directory is picked in the Project view, New > katachi > ... > `<feature>/component/<name>.kt` opens the generate
 * dialog, `settings` (a directory of `feature/`) fills `feature`, `Badge` fills `name`, and Generate writes
 * `feature/settings/.../component/Badge.kt` and opens it. Then Find Action "katachi: Generate from Template" opens the tool
 * window with the search field ready to type into.
 *
 * Written, compiled and not run by the build (`./gradlew integrationTest --tests '*CaptureCandidatesE2ETest*'` opens an IDE; run
 * by the verification lane, V4). The first Gradle import of sample/kmp (Android and KMP targets) takes the longest of the smokes.
 */
class CaptureCandidatesE2ETest {
    private val ide = SmokeIde("capture-candidates")

    @Test
    fun `featureを選んでNewメニューから開いたダイアログのfeatureをsettingsにするとその下にBadgeが生成され検索欄に打てる`() {
        val kmp = ide.prepareCopy().resolveSibling("kmp")
        val generated = kmp.resolve(GENERATED)

        ide.run(kmp, runTimeout = 60.minutes) { ideProject, bridge ->
            // 1. Pick the `feature` directory in the Project view and open New > katachi > ... > the template's item.
            openFile(ANCHOR, waitForCodeAnalysis = false)
            invokeAction("ActivateProjectToolWindow", now = false)
            val view = ideFrame().projectView()
            val tree = view.projectViewTree
            tree.waitForNodesLoaded()
            tree.rightClickRow { it == "feature" }
            val menu = ideFrame().popupMenu()
            waitFor("New in the Project view's menu", timeout = 30.seconds, errorMessage = { menu.itemsList().toString() }) { "New" in menu.itemsList() }
            menu.findMenuItemByText("New").click()
            // sample/kmp's FeatureComponent is `feature.FeatureComponent`, so the menu is katachi > `feature` (a group) > the item.
            // TODO(smoke-helper): the item's label is fixed at the first V4 run, when the menu is on screen; the walk takes the
            // one that fits the template's file name.
            selectNewMenuItem("katachi")
            selectNewMenuItem("feature")
            selectNewMenuItem { "component/" in it && it.endsWith(".kt") }

            // 2. The dialog is open on the template, with `feature` filled from the directory it was opened on or empty.
            waitFor("the generate dialog", timeout = 1.minutes, errorMessage = { bridge.diagnostics(null) }) { bridge.describeDialog().valueOf("dialog") == "open" }
            // TODO(smoke-helper): that `home` and `settings` are offered is read from the dialog's candidate list, which the bridge
            // does not answer yet (the bridge is the authoring lane's to change, A5); today the smoke picks the candidate by its value.
            bridge.shot(ide.screenshots, "1-dialog-opened")
            assertEquals("filled=feature", bridge.fillDialog("feature", "settings"))
            assertEquals("filled=name", bridge.fillDialog("name", "Badge"))
            waitFor("the target path for Badge", timeout = 30.seconds, errorMessage = { bridge.describeDialog() }) {
                bridge.describeDialog().let { it.valueOf("okEnabled") == "true" && it.valueOf("target").orEmpty().endsWith("feature/settings/$COMPONENT_PATH/Badge.kt") }
            }
            bridge.shot(ide.screenshots, "2-dialog-filled")

            // 3. Generate: the file is written under `settings` and opened.
            assertEquals("pressed=generate", bridge.pressDialog("generate"))
            waitFor("Badge.kt written", timeout = 15.minutes, interval = 3.seconds, errorMessage = { bridge.describeDialog() }) { generated.exists() }
            assertTrue("fun Badge(" in generated.readText(), generated.readText())
            waitFor("Badge.kt open in the editor", timeout = 1.minutes, interval = 1.seconds) {
                runCatching { service(FileEditorManager::class, ideProject).getCurrentFile().getPath() }.getOrDefault("").endsWith("/Badge.kt")
            }

            // 4. Find Action: the tool window comes up with the search field focused, and the typed text goes into it.
            invokeAction("katachi.GenerateFromTemplate", now = false)
            // TODO(smoke-helper): that the typed text lands in the search field is read from the bridge once it answers
            // `search=<text>` and `searchFocused=true|false` (describeState; A5 adds them, see the lane's hand-over). Until then
            // the text is judged by the screenshot; the wait below is for the tool window's list to be ready, not a fixed time.
            waitFor("the katachi tool window is ready", timeout = 1.minutes, errorMessage = { bridge.describeState() }) { bridge.describeState().valueOf("phase") == "Ready" }
            ui.keyboard { typeText("Badge", delayBetweenCharsInMs = 80) }
            bridge.shot(ide.screenshots, "3-search-typed")
        }
    }

    private fun com.intellij.driver.client.Driver.selectNewMenuItem(text: String) = selectNewMenuItem { it == text }

    private fun com.intellij.driver.client.Driver.selectNewMenuItem(matches: (String) -> Boolean) {
        val menu = ideFrame().popupMenu()
        waitFor("a New menu item that fits", timeout = 30.seconds, errorMessage = { menu.itemsList().toString() }) { menu.itemsList().any(matches) }
        menu.findMenuItemByText(menu.itemsList().first(matches)).click()
    }

    private companion object {
        const val COMPONENT_PATH = "src/commonMain/kotlin/com/example/kmp/feature/settings/component"
        const val ANCHOR = "feature/home/src/commonMain/kotlin/com/example/kmp/feature/home/HomeScreen.kt"
        const val GENERATED = "feature/settings/$COMPONENT_PATH/Badge.kt"
    }
}
