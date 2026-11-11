package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * V3 of the no-role notification (ide-display-rules-for-file plan): a real IDE opens a git copy of sample/jvm, opens a file
 * no role holds, and the notification must appear with its nearest role, open the popup under its link, change its
 * wording when the file is `git add`ed, and go away when closed. Written but not run by the build: explicitly invoked,
 * `./gradlew -p katachi-intellij-plugin integrationTest --tests '*NoRoleSmokeTest*'` (it opens IDE windows).
 *
 * What it can not see: where the popup opens relative to the link (design V4, by eye) and the colour of the warning.
 */
class NoRoleSmokeTest {
    @Test
    fun `新しく作った当たらないファイルを開くと通知が出て近い Role の popup が開き git に追加すると文言が変わり閉じると消える`() {
        val fixture = NoRoleFixture(
            Path.of(System.getProperty("katachi.smoke.sampleProject")),
            Path.of(System.getProperty("katachi.smoke.workDir")).resolve("no-role"),
        )
        val project = fixture.prepare()
        val ideHome = Path.of(System.getProperty("katachi.smoke.ideHome"))
        val ide = IdeProductProvider.IU.copy(getInstaller = { ExistingIdeInstaller(fixture.appBundle(ideHome)) })
        val context = Starter.newContext("katachiNoRole", TestCase(ide, LocalProjectInfo(project)), false)
            .apply { pluginConfigurator.installPluginFromPath(Path.of(System.getProperty("path.to.build.plugin"))) }
            .applyVMOptionsPatch {
                addSystemProperty("katachi.debugBridge", true)
                addSystemProperty("user.language", "en")
                addSystemProperty("user.country", "US")
            }
            .skipLibrarySources()
        context.runIdeWithDriver(runTimeout = SMOKE_RUN_TIMEOUT).useDriverAndCloseIde {
            waitForProjectOpen(5.minutes)
            waitForImportAndIndex(singleProject())
            val probe = NoRoleProbe(this, singleProject())
            val shots = Path.of(System.getProperty("katachi.smoke.workDir")).resolve("no-role/screenshots")
            showsForUntrackedFile(probe)
            probe.screenshot(shots.resolve("1-untracked.png"))
            popupOpensUnderTheLink(probe, shots)
            wordingChangesAfterGitAdd(fixture, probe)
            probe.screenshot(shots.resolve("3-tracked.png"))
            closeHidesIt(probe)
        }
    }

    private fun Driver.showsForUntrackedFile(probe: NoRoleProbe) {
        openFile(NoRoleFixture.STRAY, waitForCodeAnalysis = false)
        // The definition is read through Gradle on the first look: minutes, not seconds.
        waitFor("the no-role notification", timeout = 10.minutes, interval = 2.seconds, errorMessage = { probe.describe() }) {
            probe.value("noRole") == "shown"
        }
        assertEquals("file=NoRoleProbe.kt", probe.lines().first())
        assertEquals(UNTRACKED_TEXT, probe.value("text"))
        assertTrue(probe.value("nearby").orEmpty().startsWith("Nearest role: "), probe.describe())
        assertTrue("Nearby roles…" in probe.links(), probe.describe())
    }

    private fun Driver.popupOpensUnderTheLink(probe: NoRoleProbe, shots: Path) {
        // Pressed in the IDE (see NoRoleProbe.press): the notification can be rebuilt between a real press and release, and the real mouse is not ours.
        assertEquals("pressed=Nearby roles…", probe.press("Nearby roles…"))
        waitFor("the nearby roles popup", timeout = 15.seconds, interval = 1.seconds, errorMessage = { probe.describe() }) { ui.x { byVisibleText(POPUP_TITLE) }.present() }
        val rows = probe.popup()
        assertEquals("popup=open", rows.first(), rows.toString())
        assertTrue(rows.any { "Domain.Service" in it && it.endsWith("|enabled") }, "a nearby role row; $rows")
        assertTrue(rows.any { it.startsWith("row=Reload Definition") }, "the refresh row; $rows")
        assertTrue(rows.any { it.startsWith("row=Copy a role declaration draft") }, "the draft row; $rows")
        probe.screenshot(shots.resolve("2-popup-frame.png"))
        probe.screenshotPopup(shots.resolve("2-popup.png"))
        assertEquals("closed=1", probe.closePopups())
        waitFor("the popup closed", timeout = 10.seconds) { !ui.x { byVisibleText(POPUP_TITLE) }.present() }
        assertEquals("shown", probe.value("noRole"), "closing the popup must not close the notification")
    }

    private fun Driver.wordingChangesAfterGitAdd(fixture: NoRoleFixture, probe: NoRoleProbe) {
        fixture.addToGit()
        // A user coming back from the terminal focuses the IDE, which looks at the disk again; this IDE is not in front, so it is asked to.
        // Asked again on every poll: one refresh right after `git add` can run before git's own scan has seen the new index.
        waitFor("the wording after git add", timeout = 2.minutes, interval = 3.seconds, errorMessage = { probe.describe() }) {
            probe.refreshFromDisk()
            probe.value("text") == TRACKED_TEXT
        }
    }

    private fun Driver.closeHidesIt(probe: NoRoleProbe) {
        assertEquals("dismissed=true", probe.dismiss())
        waitFor("the notification closed", timeout = 30.seconds, errorMessage = { probe.describe() }) { probe.value("noRole") == "none" }
    }

    private companion object {
        const val UNTRACKED_TEXT = "No role allows this file. Once you add it to git, the test reports it as UnexpectedFile."
        const val TRACKED_TEXT = "No role allows this file. The test reports it as UnexpectedFile."
        const val POPUP_TITLE = "Nearby roles (where this file could go)"
    }
}
