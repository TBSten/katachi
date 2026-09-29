package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** G2: scenarios 4 → 1 → 2 → 3, one IDE launch, explicitly invoked integrationTest only. */
class EditorEntrySmokeTest {
    // covers: 論点0, 1, 2, 3, 4, 5, 6, 9(4), 11, 12, 13, 14, 15, 16, 17
    @Test
    fun `通知とNewから実ダイアログへつながり生成とキャンセルが働く`() {
        val fixture = EditorEntryFixture(
            Path.of(System.getProperty("katachi.smoke.sampleProject")),
            Path.of(System.getProperty("katachi.smoke.workDir")).resolve("editor-entry"),
        )
        val project = fixture.prepare()
        val ideHome = Path.of(System.getProperty("katachi.smoke.ideHome"))
        val ide = IdeProductProvider.IU.copy(getInstaller = { ExistingIdeInstaller(fixture.appBundle(ideHome)) })
        val context = Starter.newContext("katachiEditorEntry", TestCase(ide, LocalProjectInfo(project)), false)
            .apply { pluginConfigurator.installPluginFromPath(Path.of(System.getProperty("path.to.build.plugin"))) }
            .applyVMOptionsPatch {
                addSystemProperty("katachi.debugBridge", true)
                addSystemProperty("user.language", "en")
                addSystemProperty("user.country", "US")
            }
        context.runIdeWithDriver(runTimeout = 40.minutes).useDriverAndCloseIde {
            waitForProjectOpen(5.minutes)
            waitForIndicators(singleProject(), 15.minutes)
            // Do not read describeState until after View Template: it creates the view model.
            assertFalse(getToolWindow("katachi").isVisible(), "Scenario 4 requires a never-opened tool window")
            val bridge = EditorEntryProbe(this, singleProject())
            revealAndOpenSettings(bridge)
            generateFromEmptyFile(fixture, bridge)
            cancelFromNew(fixture, bridge)
            unrelatedNewHasNoKatachi()
        }
    }

    // covers: 論点3, 9(4), 11, 13, 14, 15, 17
    private fun Driver.revealAndOpenSettings(bridge: EditorEntryProbe) {
        openFile("${EditorEntryFixture.SERVICE_DIR}/EntryExistingService.kt", waitForCodeAnalysis = false)
        awaitEntryNotification("View Template")
        assertFalse(getToolWindow("katachi").isVisible())
        // The notification can be rebuilt between press and release (SmoothRobot then reports an
        // unsuccessful click), so re-look up the link and click again until the tool window opens.
        var opened = false
        repeat(6) {
            if (opened) return@repeat
            runCatching { entryNotification().x { byVisibleText("View Template") }.click() }
            opened = runCatching { waitFor("View Template opens katachi", timeout = 8.seconds) { getToolWindow("katachi").isVisible() } }.isSuccess
        }
        assertTrue(opened, "View Template never opened the katachi tool window")
        waitFor("revealed template loaded", timeout = 10.minutes, errorMessage = { bridge.describeState() }) {
            "template=${EditorEntryFixture.TEMPLATE}" in bridge.describeState().lines()
        }
        waitFor("expected template highlighted", timeout = 30.seconds, errorMessage = { bridge.describeState() }) {
            "highlighted=${EditorEntryFixture.TEMPLATE}" in bridge.describeState().lines()
        }
        openFile("${EditorEntryFixture.SERVICE_DIR}/EntrySettingsService.kt", waitForCodeAnalysis = false)
        awaitEntryNotification("View Template")
        entryNotification().x { byTooltip("Open the notification settings") }.click()
        val settings = ui.dialog(title = "Settings")
        // Page-specific controls prove that the gear selected katachi, rather than just opening Settings.
        waitFor("katachi Settings page", timeout = 30.seconds) {
            settings.x { byVisibleText("Show notifications in the editor") }.present() &&
                settings.x { byVisibleText("Notify on empty files") }.present()
        }
        settings.closeDialog()
    }

    // covers: 論点0, 1, 5, 9(4), 11, 13, 16, 17
    private fun Driver.generateFromEmptyFile(fixture: EditorEntryFixture, bridge: EditorEntryProbe) {
        val relative = "${EditorEntryFixture.SERVICE_DIR}/EntryEmptyService.kt"
        val file = fixture.project.resolve(relative)
        assertEquals(EditorEntryFixture.EMPTY_CONTENT, file.readText())
        val before = fixture.sourceSnapshot()
        openFile(relative, waitForCodeAnalysis = false)
        awaitEntryNotification("Create")
        entryNotification().x { byVisibleText("Create") }.click()
        awaitEntryDialog(bridge, "dialog=open", "template=${EditorEntryFixture.TEMPLATE}", "field=name=EntryEmpty", "field=body=", "okEnabled=false")
        assertTrue(bridge.describeDialog().lines().any { it.startsWith("target=") && it.endsWith(relative) }, bridge.describeDialog())
        // The path determines name, but body must be requested. Compose focuses its first
        // empty required input, so actual keyboard events fill the missing parameter.
        ui.keyboard { typeText("Generated by the editor entry", delayBetweenCharsInMs = 80) }
        awaitEntryDialog(bridge, "field=body=Generated by the editor entry", "okEnabled=true")
        ui.keyboard { hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_TAB) }
        // Reverse-captured values are editable; the preview must follow. Restore to exercise
        // overwriting the original empty file, not merely creating a different file.
        typeEntryCapture("EntryEdited", bridge)
        awaitEntryDialog(bridge, "okEnabled=true")
        assertTrue(bridge.describeDialog().lines().any { it.startsWith("target=") && it.endsWith("EntryEditedService.kt") })
        typeEntryCapture("EntryEmpty", bridge)
        awaitEntryDialog(bridge, "okEnabled=true")
        assertTrue(bridge.describeDialog().lines().any { it.startsWith("target=") && it.endsWith(relative) })
        assertEquals(EditorEntryFixture.EMPTY_CONTENT, file.readText(), "Editing the form must not write the placeholder")
        ui.dialog(title = "katachi: Generate from Template").x { byVisibleText("Generate") }.click()
        awaitEntryDialog(bridge, "dialog=none")
        val expected = EditorEntryFixture.content("EntryEmpty")
        waitFor("empty file overwritten with generated content", timeout = 10.minutes, interval = 2.seconds) { file.readText() == expected }
        assertEntryEditor(file.toString(), expected)
        waitFor("notification disappears after generation", timeout = 30.seconds) { !entryNotification().present() }
        val after = fixture.sourceSnapshot()
        assertEquals(before.keys, after.keys, "One selection must not generate extra files")
        assertEquals(setOf(relative), after.keys.filter { before[it] != after[it] }.toSet())
    }

    // covers: 論点1, 2, 6, 9(4), 11, 12, 17
    private fun Driver.cancelFromNew(fixture: EditorEntryFixture, bridge: EditorEntryProbe) {
        val before = fixture.sourceSnapshot()
        val vfsBefore = entryVfsSnapshot(fixture.project.resolve(EditorEntryFixture.SERVICE_DIR).toString())
        openEntryNewMenu("${EditorEntryFixture.SERVICE_DIR}/EntryEmptyService.kt")
        // Scenario 4 already loaded the index; the optional cold/loading item (論点18)
        // cannot be observed deterministically in this single-IDE sequence.
        selectEntryMenuItem("katachi")
        selectEntryMenuItem("domain")
        selectEntryMenuItem("Service")
        waitFor("both templates expose the remaining path", timeout = 10.seconds) {
            val templates = ideFrame().popupMenu().itemsList()
            "Helper source — <name>Helper.kt" in templates && "Service source — <name>Service.kt" in templates
        }
        selectEntryMenuItem("Service source — <name>Service.kt")
        awaitEntryDialog(bridge, "dialog=open", "template=${EditorEntryFixture.TEMPLATE}", "field=name=", "canGenerate=false", "okEnabled=false")
        // An undecided capture is required; typing makes it valid but does not write anything.
        ui.keyboard { typeText("EntryCancelled", delayBetweenCharsInMs = 80) }
        awaitEntryDialog(bridge, "field=name=EntryCancelled", "okEnabled=false")
        ui.keyboard { hotKey(KeyEvent.VK_TAB); typeText("Cancelled body", delayBetweenCharsInMs = 80) }
        awaitEntryDialog(bridge, "field=body=Cancelled body", "okEnabled=true")
        assertTrue(bridge.describeDialog().lines().any { it.startsWith("target=") && it.endsWith("EntryCancelledService.kt") })
        // One Esc must be enough: Compose used to keep the key while an input field had focus.
        ui.keyboard { escape() }
        awaitEntryDialog(bridge, "dialog=none")
        waitFor("dialog window closed", timeout = 10.seconds) {
            !ui.dialog(title = "katachi: Generate from Template").present()
        }
        waitForIndicators(singleProject(), 1.minutes)
        assertEquals(vfsBefore, entryVfsSnapshot(fixture.project.resolve(EditorEntryFixture.SERVICE_DIR).toString()), "Esc must not add VFS entries")
        assertEquals(before, fixture.sourceSnapshot(), "Esc must leave all source files and directories unchanged")
    }

    // covers: 論点3, 4, 9(4), 12
    private fun Driver.unrelatedNewHasNoKatachi() {
        // application.conf is an existing resource in the sample. Selecting its parent gives a
        // real resource directory, which none of this fixture's templates can generate into.
        openEntryNewMenu("src/main/resources/application.conf")
        val items = ideFrame().popupMenu().itemsList()
        assertFalse(items.any { it.contains("katachi", ignoreCase = true) }, "Unexpected katachi item: $items")
        ui.keyboard { escape(); escape() }
    }
}
