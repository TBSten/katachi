package me.tbsten.katachi.intellij

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.openToolWindow
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.accessibleName
import com.intellij.driver.sdk.ui.boundsOnScreen
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.awt.Point
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/** [me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge], seen from Driver (a copy of [KatachiDebugBridgeRef]: S3 only reads it). */
@Remote("me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge", plugin = "me.tbsten.katachi.intellij")
interface SpikeDebugBridgeRef {
    fun describeState(): String
}

/**
 * Spike (task S3): tries, once, the four ways a later E2E test needs to reach the real UI, and
 * writes down what worked as `/Users/tbsten/dev/katachi/.local/idea-plugin/impl/spike-driver.md`.
 * (a) the project tree's right-click → New → submenu (b) typing into the tool window's Compose
 * search field, which Driver's Swing tree cannot see into (c) finding a Swing dialog (Settings) by
 * title and closing it. All three run in one IDE launch, the way the existing smokes do.
 *
 * `katachi`'s own New menu group does not exist yet (task K1 adds it), so (a) exercises the
 * mechanics — right-click, walk a submenu, close without picking anything — against whatever
 * "New" already offers on a plain Kotlin/JVM project; G2 (task S3's own consumer) is the one that
 * repeats this against the katachi item once K1 lands.
 */
@Suppress("NewApi")
class SpikeDriverTest {
    private val sample: Path = Paths.get(System.getProperty("katachi.smoke.sampleProject"))
    private val ideHome: Path = Paths.get(System.getProperty("katachi.smoke.ideHome"))
    private val workDir: Path = Paths.get(System.getProperty("katachi.smoke.workDir")).resolve("spike-driver")

    @Test
    fun `右クリックのNewサブメニュー・ツールウィンドウの検索欄への入力・Settingsダイアログの開閉を1回のIDE起動で試す`() {
        keepStarterFilesUnder(workDir.resolve("starter"))
        val project = copyOfSample()
        val ide = IdeProductProvider.IU.copy(getInstaller = { ExistingIdeInstaller(appBundleOf(ideHome)) })
        val context = Starter.newContext(
            "katachiSpikeDriver",
            TestCase(ide, LocalProjectInfo(project.resolve("sample/jvm"))),
            false,
        ).apply { pluginConfigurator.installPluginFromPath(Paths.get(System.getProperty("path.to.build.plugin"))) }
            // The debug bridge (used here only to wait for the template list to load) refuses to
            // act unless the IDE runs with this flag.
            .applyVMOptionsPatch { addSystemProperty("katachi.debugBridge", true) }

        // `useDriverAndCloseIde` always returns `IDEStartResult`, not the lambda's own result
        // (unlike `measureTimedValue`'s block), so the scenarios' findings are collected into this
        // instead of a return value.
        lateinit var result: SpikeDriverResult
        val elapsed = measureTimedValue {
            context.runIdeWithDriver(runTimeout = 40.minutes).useDriverAndCloseIde {
                waitForProjectOpen(5.minutes)
                val ideProject = singleProject()
                waitForIndicators(ideProject, 15.minutes)

                // (e) locator candidate for "the tool window is visible": the platform's own
                // ToolWindowManager, no Swing scraping needed. Cheap, so tried before anything else
                // (the "does 1 click land" check the prompt asks for).
                val visibleBeforeOpen = step("(e) katachi tool window visible before it is opened") { getToolWindow("katachi").isVisible() }

                openToolWindow("katachi")
                val bridge = service(SpikeDebugBridgeRef::class, ideProject)
                waitFor("the template list", timeout = 10.minutes, interval = 2.seconds, errorMessage = { bridge.describeState() }) {
                    "template=domain.Service" in bridge.describeState()
                }
                val visibleAfterOpen = step("(e) katachi tool window visible after it is opened") { getToolWindow("katachi").isVisible() }

                // (a) Project tree: right-click the root, open New, list what a submenu shows, close
                // without picking anything (a spike should not mutate the copy it is not checking).
                val newItems = step("(a) project tree right-click -> New -> submenu") {
                    val frame = ideFrame()
                    val tree = frame.projectView().projectViewTree
                    // The first attempt (no wait) landed nowhere: SmoothRobot logged "Click was
                    // unsuccessful" and the popup never appeared. Waiting for the tree's nodes to
                    // finish loading, then focusing the row with a left click before the real
                    // right click, is the fix research-ide-testing.md 2.3 did not need to spell out.
                    tree.waitForNodesLoaded()
                    tree.clickRow(0)
                    tree.rightClickRow(0)
                    val menu = frame.popupMenu()
                    val topLevel = menu.itemsList()
                    check("New" in topLevel) { "no 'New' item in the context menu: $topLevel" }
                    menu.findMenuItemByText("New").click()
                    // The submenu opens as a second popup; everything ActionMenuItem-shaped that
                    // is visible now is logged (parent items + New's children both may be on screen).
                    waitFor(
                        "New's submenu items",
                        timeout = 5.seconds,
                        errorMessage = { "items stayed at $topLevel, New's submenu did not add any" },
                    ) { frame.popupMenu().itemsList().size > topLevel.size }
                    val items = frame.popupMenu().itemsList()
                    // Close both popups without creating anything.
                    ui.keyboard { escape() }
                    ui.keyboard { escape() }
                    items
                }

                // (b) The tool window's search field lives inside one ComposePanel Driver's Swing
                // tree cannot see into (research-ide-testing.md 2.3); the candidate is to click a
                // point inside the panel's Swing host and send real keystrokes there. There is no
                // way from this test to read the Compose state back (no bridge method exposes the
                // search query), so this only confirms the keys can be sent without the IDE erroring.
                step("(b) type into the tool window's Compose search field") {
                    val panel = ui.x { byClass("JewelComposePanelWrapper") }
                    val bounds = panel.boundsOnScreen
                    println("[spike-driver] JewelComposePanelWrapper bounds: $bounds")
                    // The search field is the Column's first item (no refresh bar/banner once the
                    // template list has loaded, confirmed above), padded 6dp from the top.
                    panel.click(Point(bounds.width / 2, 18))
                    panel.keyboard { typeText("Serv", delayBetweenCharsInMs = 80) }
                    panel.keyboard { escape() }
                }

                // (c) A Swing dialog (Settings), found by title and closed.
                step("(c) find the Settings dialog by title and close it") {
                    ideFrame().openSettingsDialog()
                    val settings = ui.dialog(title = "Settings")
                    println("[spike-driver] Settings dialog accessible name: ${settings.accessibleName}")
                    settings.closeDialog()
                }

                result = SpikeDriverResult(visibleBeforeOpen, visibleAfterOpen, newItems)
            }
        }.duration

        println("[spike-driver] one run took $elapsed")
        println("[spike-driver] tool window visible before open=${result.visibleBeforeOpen}, after open=${result.visibleAfterOpen}")
        println("[spike-driver] New submenu items seen: ${result.newSubmenuItems}")
    }

    private data class SpikeDriverResult(val visibleBeforeOpen: Boolean, val visibleAfterOpen: Boolean, val newSubmenuItems: List<String>)

    /**
     * Starter puts its IDE copies, configs and logs under `<git root>/out/ide-tests` by default,
     * which here is the katachi repository itself; move them under [directory].
     */
    private fun keepStarterFilesUnder(directory: Path) {
        Files.createDirectories(directory)
        di = DI {
            extend(di)
            bindSingleton<GlobalPaths>(overrides = true) { object : GlobalPaths(directory) {} }
        }
    }

    /**
     * The IDE the build resolved is the `Contents` of a macOS bundle, and Starter only accepts a
     * `.app` directory, so the bundle is rebuilt around a copy of it. A copy and not a symbolic
     * link: macOS kills (exit 137) a signed launcher whose bundle is a link to elsewhere.
     */
    private fun appBundleOf(contents: Path): Path {
        val bundle = workDir.resolve("ide/IntelliJ IDEA.app")
        if (!Files.exists(bundle.resolve("Contents/Info.plist"))) {
            run(workDir, "rm", "-rf", bundle.toString())
            Files.createDirectories(bundle)
            run(workDir, "ditto", contents.toString(), bundle.resolve("Contents").toString())
        }
        return bundle
    }

    /**
     * sample/jvm includes the whole repository (`includeBuild("../..")`), so the repository is
     * copied without build output, and sample/jvm inside the copy is what the IDE opens. `git init`
     * keeps katachi's `gitTracked()` working in the copy. Returns the root of the copy.
     */
    private fun copyOfSample(): Path {
        val repository = sample.resolve("../..").normalize()
        val destination = workDir.resolve("repository")
        run(workDir, "rm", "-rf", destination.toString())
        Files.createDirectories(destination)
        run(
            workDir, "rsync", "-a",
            "--exclude", "build/", "--exclude", ".gradle/", "--exclude", ".kotlin/", "--exclude", ".idea/",
            "--exclude", ".git", "--exclude", ".intellijPlatform/", "--exclude", "/katachi-intellij-plugin/",
            "--exclude", "/.local/",
            "$repository/", "$destination/",
        )
        run(destination, "git", "init", "-q")
        return destination
    }

    private fun run(directory: Path, vararg command: String) {
        Files.createDirectories(directory)
        val process = ProcessBuilder(*command).directory(directory.toFile()).inheritIO().start()
        check(
            process.waitFor(5, TimeUnit.MINUTES) &&
                    process.exitValue() == 0
        ) { "${command.joinToString(" ")} failed" }
    }
}
