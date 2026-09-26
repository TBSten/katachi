package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.openToolWindow
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The plugin's [me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge], seen from Driver. */
@Remote("me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge", plugin = "me.tbsten.katachi.intellij")
interface KatachiDebugBridgeRef {
    fun describeState(): String

    fun checkAndFill(roleName: String, args: String): Boolean

    fun generate()
}

/**
 * The Driver smoke (channel D): a real IntelliJ IDEA with the built plugin opens a copy of
 * sample/jvm, imports it with Gradle, opens the `katachi` tool window, sees the template list, checks
 * `domain/Service`, fills `name`, generates, and finds the written file open in the editor.
 *
 * The rows and fields live in one `ComposePanel` that Driver's Swing tree cannot see, so everything
 * after opening the tool window goes through the plugin's debug bridge, which sends the intents a
 * click would.
 */
class ToolWindowSmokeTest {
    private val sample: Path = Paths.get(System.getProperty("katachi.smoke.sampleProject"))
    private val ideHome: Path = Paths.get(System.getProperty("katachi.smoke.ideHome"))
    private val workDir: Path = Paths.get(System.getProperty("katachi.smoke.workDir"))

    @Test
    fun `sample-jvmでツールウィンドウから1件生成するとそのファイルがエディタで開く`() {
        keepStarterFilesUnder(workDir.resolve("starter"))
        val project = copyOfSample()
        val ide = IdeProductProvider.IU.copy(getInstaller = { ExistingIdeInstaller(appBundleOf(ideHome)) })
        val context = Starter.newContext("katachiToolWindowSmoke", TestCase(ide, LocalProjectInfo(project.resolve("sample/jvm"))), false)
            .apply { pluginConfigurator.installPluginFromPath(Paths.get(System.getProperty("path.to.build.plugin"))) }
            // The plugin's debug bridge acts only when asked to.
            .applyVMOptionsPatch { addSystemProperty("katachi.debugBridge", true) }

        // The first Gradle import and load of the whole repository take minutes; the default is 10.
        context.runIdeWithDriver(runTimeout = 40.minutes).useDriverAndCloseIde {
            waitForProjectOpen(5.minutes)
            val ideProject = singleProject()
            // Opening a directory with a Gradle build links and imports it by itself.
            // (driver-sdk's importGradleProject needs the performance-testing plugin, which is not installed.)
            waitForIndicators(ideProject, 15.minutes)

            openToolWindow("katachi")
            val bridge = service(KatachiDebugBridgeRef::class, ideProject)
            waitFor("the template list", timeout = 10.minutes, interval = 2.seconds, errorMessage = { bridge.describeState() }) {
                "template=domain/Service" in bridge.describeState()
            }

            assertTrue(bridge.checkAndFill("domain/Service", "name=SmokeProbe"), bridge.describeState())
            bridge.generate()
            waitFor("the generation to finish", timeout = 10.minutes, interval = 2.seconds, errorMessage = { bridge.describeState() }) {
                "generation=finished" in bridge.describeState()
            }
            val state = bridge.describeState()
            assertTrue("result=domain/Service:Generated" in state, state)

            val written = project.resolve("sample/jvm/src/main/kotlin/com/example/service/SmokeProbeService.kt")
            assertTrue(Files.isRegularFile(written), "$written was not written")
            waitFor("the written file to open", timeout = 1.minutes, interval = 1.seconds, errorMessage = { openFileOf(ideProject) }) {
                openFileOf(ideProject).endsWith("/SmokeProbeService.kt")
            }
        }
    }

    private fun Driver.openFileOf(ideProject: com.intellij.driver.sdk.Project): String =
        // Declared non-null by the SDK, but there is no current file until one opens.
        runCatching { service(FileEditorManager::class, ideProject).getCurrentFile().getPath() }.getOrDefault("")

    /**
     * Starter puts its IDE copies, configs and logs under `<git root>/out/ide-tests` by default,
     * which here is the katachi repository itself; move them under the build directory.
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
            "$repository/", "$destination/",
        )
        run(destination, "git", "init", "-q")
        return destination
    }

    private fun run(directory: Path, vararg command: String) {
        Files.createDirectories(directory)
        val process = ProcessBuilder(*command).directory(directory.toFile()).inheritIO().start()
        check(process.waitFor(5, TimeUnit.MINUTES) && process.exitValue() == 0) { "${command.joinToString(" ")} failed" }
    }
}
