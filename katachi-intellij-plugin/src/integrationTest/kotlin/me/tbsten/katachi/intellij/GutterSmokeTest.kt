package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The plugin's [me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge]'s gutter half, seen from Driver. */
@Remote("me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge", plugin = "me.tbsten.katachi.intellij")
interface GutterBridgeRef {
    fun describeGutter(path: String): String

    fun describeHighlights(path: String): String

    fun clickGutter(path: String, line: Int): String

    fun chooseFromGutter(path: String, line: Int, row: String): String

    fun screenshotFrame(out: String): String

    fun screenshotGutterMenu(path: String, line: Int, out: String): String
}

/**
 * The Driver smoke of the source-file gutter (plan P5, V4): a real IntelliJ IDEA with the built plugin opens a copy of sample/jvm,
 * and `HealthService.kt` gets a ◆ that says which line of `ServiceRole.kt` allowed `class HealthService` (the gutter asks the
 * definition to load by itself: nothing here opens the tool window). The ◆'s menu lists the other files of the role and the
 * template for the next one, choosing the allowed-by row opens `ServiceRole.kt`, and running the check from the menu draws an
 * underline under the `internal class` the copy was planted with.
 *
 * Everything the smoke reads goes through the debug bridge, which gives what the daemon drew as `key=value` lines. Not part of
 * `check`: it starts an IDE (`./gradlew -p katachi-intellij-plugin integrationTest --tests '*GutterSmokeTest*'`, 2 to 3 minutes
 * once the IDE and the copy are there).
 */
@Suppress("NewApi")
class GutterSmokeTest {
    private val sample: Path = Paths.get(System.getProperty("katachi.smoke.sampleProject"))
    private val ideHome: Path = Paths.get(System.getProperty("katachi.smoke.ideHome"))
    private val workDir: Path = Paths.get(System.getProperty("katachi.smoke.workDir")).resolve("gutter")

    @Test
    fun `sample-jvmのソースを開くと宣言の◆が許可元を言い メニューから許可元へ飛べ 検査の後に違反の下線が出る`() {
        keepStarterFilesUnder(workDir.resolve("starter"))
        val repository = copyOfSample()
        val project = repository.resolve("sample/jvm")
        // A class the "Must be public" constraint rejects, in a file the Service role holds.
        val probe = project.resolve("$SERVICE_DIR/ProbeService.kt")
        Files.writeString(probe, "package com.example.service\n\ninternal class ProbeService\n")

        val ide = IdeProductProvider.IU.copy(getInstaller = { ExistingIdeInstaller(appBundleOf(ideHome)) })
        val context = Starter.newContext("katachiGutterSmoke", TestCase(ide, LocalProjectInfo(project)), false)
            .apply { pluginConfigurator.installPluginFromPath(Paths.get(System.getProperty("path.to.build.plugin"))) }
            .applyVMOptionsPatch {
                addSystemProperty("katachi.debugBridge", true)
                // The texts the smoke asserts are English.
                addSystemProperty("user.language", "en")
                addSystemProperty("user.country", "US")
            }

        // The first Gradle import and the definition task take minutes.
        context.runIdeWithDriver(runTimeout = 40.minutes).useDriverAndCloseIde {
            waitForProjectOpen(5.minutes)
            val ideProject = singleProject()
            waitForIndicators(ideProject, 15.minutes)
            val bridge = service(GutterBridgeRef::class, ideProject)
            val shots = workDir.resolve("screenshots")
            val service = project.resolve("$SERVICE_DIR/HealthService.kt").toString()
            val probePath = probe.toString()

            // 1. The ◆: opening the file is what makes the gutter ask the definition to load.
            openFile("$SERVICE_DIR/HealthService.kt", waitForCodeAnalysis = false)
            waitFor("the ◆ on HealthService", timeout = 15.minutes, interval = 3.seconds, errorMessage = { bridge.describeGutter(service) }) {
                "allowed by internal.optional() class HealthService" in bridge.describeGutter(service)
            }
            val gutter = bridge.describeGutter(service).lines()
            assertTrue("mark=7|menu|HealthService|katachi: domain.Service — allowed by internal.optional() class HealthService (ServiceRole.kt:80)" in gutter, gutter.toString())
            assertEquals(1, gutter.count { it.startsWith("mark=") }, gutter.toString())

            // 2. The menu: the other files of the role, the template for the next file, and the way to the rule.
            // What the user sees, painted by the IDE itself (Starter's desktop screenshot is the wallpaper without screen-recording permission).
            println("screenshot " + bridge.screenshotFrame(shots.resolve("1-service-gutter.png").toString()))
            println("screenshot " + bridge.screenshotGutterMenu(service, 7, shots.resolve("2-service-gutter-menu.png").toString()))
            Thread.sleep(10_000)
            println("screenshot " + bridge.screenshotFrame(shots.resolve("1b-service-gutter-10s-later.png").toString()))
            val menu = bridge.clickGutter(service, 7).lines()
            assertEquals("menu=HealthService — domain.Service", menu.first())
            assertTrue("item=◆ Allowed by internal.optional() class HealthService|ServiceRole.kt:80|enabled" in menu, menu.toString())
            assertTrue(menu.any { it.startsWith("item=LegacyStatusService.kt|") }, menu.toString())
            assertTrue(menu.any { it.startsWith("item=+ New domain.Service from template") || it.startsWith("submenu=+ New domain.Service from template") }, menu.toString())
            assertTrue("item=⟳ Reload Definition||enabled" in menu, menu.toString())

            // 3. Choosing the allowed-by row opens the line of ServiceRole.kt that allowed the declaration.
            assertEquals("chosen=◆ Allowed by internal.optional() class HealthService", bridge.chooseFromGutter(service, 7, "◆ Allowed by"))
            waitFor("ServiceRole.kt in the editor", timeout = 1.minutes, interval = 1.seconds, errorMessage = { openFileOf(ideProject) }) {
                openFileOf(ideProject).endsWith("/ServiceRole.kt")
            }

            // 4. A file the gutter drew for stays drawn in another file of the role, and the check's violation underlines the planted class.
            openFile("$SERVICE_DIR/ProbeService.kt", waitForCodeAnalysis = false)
            waitFor("the ◆ on ProbeService", timeout = 2.minutes, interval = 2.seconds, errorMessage = { bridge.describeGutter(probePath) }) {
                "katachi: domain.Service" in bridge.describeGutter(probePath)
            }
            // Run from the ◆ of the file on screen: the platform ignores an action whose editor is not showing (HealthService.kt's tab is behind).
            val chosen = bridge.chooseFromGutter(probePath, 3, "▶ Run the check")
            assertTrue(chosen.startsWith("chosen=▶ Run the check (:") && chosen.endsWith(":test)"), chosen)
            waitFor("the underline under ProbeService", timeout = 20.minutes, interval = 5.seconds, errorMessage = { bridge.describeHighlights(probePath) }) {
                bridge.describeHighlights(probePath).lines().any { "rejected ProbeService" in it }
            }
            val highlights = bridge.describeHighlights(probePath).lines().filter { it.startsWith("highlight=") }
            assertTrue(highlights.any { it.startsWith("highlight=ERROR|3:16-3:28|katachi: \"Must be public\"") && it.endsWith("Run the check again") }, highlights.toString())
            println("screenshot " + bridge.screenshotFrame(shots.resolve("3-probe-underline.png").toString()))
            println("screenshot " + bridge.screenshotGutterMenu(probePath, 3, shots.resolve("4-probe-gutter-menu.png").toString()))
            // Back to the first file: the ◆ drawn earlier is still on screen.
            openFile("$SERVICE_DIR/HealthService.kt", waitForCodeAnalysis = false)
            Thread.sleep(5_000)
            println("screenshot " + bridge.screenshotFrame(shots.resolve("5-service-again.png").toString()))
            takeScreenshot("katachiGutter")
        }
    }

    private fun Driver.openFileOf(ideProject: Project): String =
        // Declared non-null by the SDK, but there is no current file until one opens.
        runCatching { service(FileEditorManager::class, ideProject).getCurrentFile().getPath() }.getOrDefault("")

    /** Starter puts its IDE copies, configs and logs under the git root of the repository by default; keep them under the build directory. */
    private fun keepStarterFilesUnder(directory: Path) {
        Files.createDirectories(directory)
        di = DI {
            extend(di)
            bindSingleton<GlobalPaths>(overrides = true) { object : GlobalPaths(directory) {} }
        }
    }

    /** The IDE the build resolved is the `Contents` of a macOS bundle, and Starter only accepts a `.app`: the bundle is rebuilt around a copy of it. */
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
     * sample/jvm includes the whole repository (`includeBuild("../..")`), so the repository is copied without build output;
     * `git init` keeps katachi's `gitTracked()` working in the copy. Returns the root of the copy.
     */
    private fun copyOfSample(): Path {
        val repository = sample.resolve("../..").normalize()
        val destination = workDir.resolve("repository")
        run(workDir, "rm", "-rf", destination.toString())
        Files.createDirectories(destination)
        run(workDir, "rsync", "-a", *REPOSITORY_COPY_EXCLUDES, "$repository/", "$destination/")
        run(destination, "git", "init", "-q")
        return destination
    }

    private fun run(directory: Path, vararg command: String) {
        Files.createDirectories(directory)
        val process = ProcessBuilder(*command).directory(directory.toFile()).inheritIO().start()
        check(process.waitFor(5, TimeUnit.MINUTES) && process.exitValue() == 0) { "${command.joinToString(" ")} failed" }
    }

    private companion object {
        const val SERVICE_DIR = "src/main/kotlin/com/example/service"
    }
}
