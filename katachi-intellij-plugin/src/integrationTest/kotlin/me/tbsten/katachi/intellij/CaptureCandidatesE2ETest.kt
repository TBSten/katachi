package me.tbsten.katachi.intellij

import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.waitFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The capture candidates in a real IDE (ide-review-screens plan 4.6): a copy of sample/kmp is opened and imported, New >
 * katachi on the `feature/` directory (through the debug bridge, not the Project view: Driver's real mouse and keyboard do not
 * reach a local macOS IDE, see [SmokeBridge]) > ... > `<feature>/component/<name>.kt` opens the generate
 * dialog, `settings` (a directory of `feature/`) fills `feature`, `Badge` fills `name`, and Generate writes
 * `feature/settings/.../component/Badge.kt` and opens it. Then Find Action "katachi: Generate from Template" opens the tool
 * window, which has finished loading (`phase=Ready`). Typing into its search field is not done or checked: a real keyboard does not reach
 * a local macOS IDE, and this test is not in the nightly CI (screens C-14), so no run exercises it; the bridge has no `search=` line yet.
 *
 * Screenshots go to `build/integrationTest/capture-candidates/screenshots`: the generate dialog's window as the screen shows it
 * (`bridge.screenshotDialog`, which needs the OS's screen-capture permission; without it nothing is written and the answer is
 * `saved=none`, as on the machine this was written on, so the dialog's picture is then only by eye in V4) and the frame with the tool window. The candidate list of the dialog is Compose inside the dialog, not a
 * popup of its own, so `screenshotPopup` finds nothing there.
 *
 * Written, compiled and not run by the build (`./gradlew integrationTest --tests '*CaptureCandidatesE2ETest*'` opens an IDE; run
 * by the verification lane, V4). The first Gradle import of sample/kmp (Android and KMP targets) takes the longest of the smokes.
 */
class CaptureCandidatesE2ETest {
    private val ide = SmokeIde("capture-candidates")

    @Test
    fun `featureを選んでNewメニューから開いたダイアログのfeatureをsettingsにするとその下にBadgeが生成されツールウィンドウが開く`() {
        // The copy is a git repository: the "Add to Git?" question of the generated file is answered in advance.
        val kmp = ide.prepareCopy { jvm -> ide.answerVcsAddSilently(jvm.resolveSibling("kmp")) }.resolveSibling("kmp")
        val generated = kmp.resolve(GENERATED)

        ide.run(kmp, runTimeout = 60.minutes) { ideProject, bridge ->
            // 1. New > katachi on the `feature` directory, through the bridge: Driver's real mouse and keyboard do not reach the IDE
            // on a local macOS, so the Project view's right-click and the menu clicks are replaced by the group's own items.
            openFile(ANCHOR, waitForCodeAnalysis = false)
            val featureDirectory = kmp.resolve("feature").toString()
            // TODO(smoke-helper): the item's label is fixed at the first run when the menu is on screen; the wait takes the one that
            // fits the template's file name (`component/` ... `.kt`) under katachi > `Feature`.
            var item = ""
            waitFor("the New > katachi item of the component template", timeout = 3.minutes, interval = 3.seconds, errorMessage = { bridge.describeNewMenu(featureDirectory) }) {
                item = bridge.describeNewMenu(featureDirectory).lines().filter { it.endsWith("|enabled") }.map { it.removePrefix("item=").removeSuffix("|enabled") }
                    .firstOrNull { it.startsWith("katachi/Feature/") && "component/" in it && it.endsWith(".kt") }.orEmpty()
                item.isNotEmpty()
            }
            assertEquals("chosen=${item.removePrefix("katachi/Feature/")}", bridge.chooseNewMenuItemLater(featureDirectory, item))

            // 2. The dialog is open on the template, with `feature` filled from the directory it was opened on or empty.
            waitFor("the generate dialog", timeout = 1.minutes, errorMessage = { bridge.diagnostics(null) }) { bridge.describeDialog().valueOf("dialog") == "open" }
            // TODO(smoke-helper): that `home` and `settings` are offered is read from the dialog's candidate list, which the bridge
            // does not answer yet (the bridge is the authoring lane's to change, A5); today the smoke picks the candidate by its value.
            // The dialog is its own window, which `shot` (the frame) leaves out, and its ComposePanel cannot be painted into an image
            // from the bridge: the window is taken from the screen. No permission means `saved=none`, and the dialog is then looked at by eye (V4).
            val opened = bridge.screenshotDialog(ide.screenshots.resolve("1-dialog-opened.png").toString())
            assertEquals("filled=feature", bridge.fillDialog("feature", "settings"))
            assertEquals("filled=name", bridge.fillDialog("name", "Badge"))
            waitFor("the target path for Badge", timeout = 30.seconds, errorMessage = { bridge.describeDialog() }) {
                bridge.describeDialog().let { it.valueOf("okEnabled") == "true" && it.valueOf("target").orEmpty().endsWith("feature/settings/$COMPONENT_PATH/Badge.kt") }
            }
            val filled = bridge.screenshotDialog(ide.screenshots.resolve("2-dialog-filled.png").toString())
            println("dialog screenshots (saved=none without the OS screen-capture permission): $opened, $filled")

            // 3. Generate: the file is written under `settings` and opened.
            assertEquals("pressed=generate", bridge.pressDialog("generate"))
            // The file first holds the "generating" placeholder; the template's content replaces it when the Gradle run ends.
            waitFor("Badge.kt written", timeout = 15.minutes, interval = 3.seconds, errorMessage = { bridge.describeDialog() + "\n" + runCatching { generated.readText() } }) {
                generated.exists() && "fun Badge(" in generated.readText()
            }
            waitFor("Badge.kt open in the editor", timeout = 1.minutes, interval = 1.seconds) {
                runCatching { service(FileEditorManager::class, ideProject).getCurrentFile().getPath() }.getOrDefault("").endsWith("/Badge.kt")
            }

            // 4. Find Action: the tool window comes up and has loaded. The search field is not typed into (see the class's KDoc).
            invokeAction("katachi.GenerateFromTemplate", now = false)
            waitFor("the katachi tool window is ready", timeout = 1.minutes, errorMessage = { bridge.describeState() }) { bridge.describeState().valueOf("phase") == "Ready" }
            bridge.shot(ide.screenshots, "3-tool-window-ready")
        }
    }

    private companion object {
        const val COMPONENT_PATH = "src/commonMain/kotlin/com/example/kmp/feature/settings/component"
        const val ANCHOR = "feature/home/src/commonMain/kotlin/com/example/kmp/feature/home/HomeScreen.kt"
        const val GENERATED = "feature/settings/$COMPONENT_PATH/Badge.kt"
    }
}
