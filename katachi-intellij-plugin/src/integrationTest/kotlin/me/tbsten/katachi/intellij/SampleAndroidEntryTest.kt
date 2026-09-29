package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * V1: G2 scenario 1 (notification -> dialog -> generation) on a copy of sample/android, whose
 * templates have a module capture (`:feature:${capture("feature")}`). Explicitly invoked only.
 */
class SampleAndroidEntryTest {
    @Test
    fun `sample android のモジュール capture が逆引きされ本物の katachiTemplate が仮のファイルを上書きする`() {
        val workDir = Path.of(System.getProperty("katachi.smoke.workDir")).resolve("sample-android")
        Files.createDirectories(workDir)
        Files.newDirectoryStream(workDir).use { dirs ->
            dirs.filter { it.fileName.toString().let { n -> n.startsWith("ide-") || n.startsWith("repository-") } }
                .forEach { it.toFile().deleteRecursively() }
        }
        val starter = workDir.resolve("starter")
        di = DI {
            extend(di)
            bindSingleton<GlobalPaths>(overrides = true) { object : GlobalPaths(starter) {} }
        }
        val repository = Files.createTempDirectory(workDir, "repository-")
        val source = Path.of(System.getProperty("katachi.smoke.sampleProject")).resolve("../..").normalize()
        run(workDir, "rsync", "-a", "--exclude", "build/", "--exclude", ".gradle/", "--exclude", ".kotlin/",
            "--exclude", ".idea/", "--exclude", ".git", "--exclude", ".intellijPlatform/",
            "--exclude", "/.local/", "--exclude", "/katachi-intellij-plugin/", "$source/", "$repository/")
        val project = repository.resolve("sample/android")
        val relative = "feature/home/src/main/kotlin/com/example/sample/feature/home/component/HomeUserCard.kt"
        val placeholder = "  \npackage com.example.sample.feature.home.component\n\n// Placeholder\n/* Generate here. */\n  "
        Files.createDirectories(project.resolve(relative).parent)
        project.resolve(relative).writeText(placeholder)
        // gitTracked() reads `git ls-files`: the copy has no .git, so make it a repository.
        run(project, "git", "init", "-q")
        run(project, "git", "add", "-A")

        val ideHome = Path.of(System.getProperty("katachi.smoke.ideHome"))
        val bundle = Files.createTempDirectory(workDir, "ide-").resolve("IntelliJ IDEA.app")
        Files.createDirectories(bundle)
        run(workDir, "ditto", ideHome.toString(), bundle.resolve("Contents").toString())
        val ide = IdeProductProvider.IU.copy(getInstaller = { ExistingIdeInstaller(bundle) })
        val context = Starter.newContext("katachiSampleAndroid", TestCase(ide, LocalProjectInfo(project)), false)
            .apply { pluginConfigurator.installPluginFromPath(Path.of(System.getProperty("path.to.build.plugin"))) }
            .applyVMOptionsPatch {
                addSystemProperty("katachi.debugBridge", true)
                addSystemProperty("user.language", "en")
                addSystemProperty("user.country", "US")
            }
        context.runIdeWithDriver(runTimeout = 60.minutes).useDriverAndCloseIde {
            waitForProjectOpen(5.minutes)
            waitForIndicators(singleProject(), 20.minutes)
            val bridge = EditorEntryProbe(this, singleProject())
            openFile(relative, waitForCodeAnalysis = false)
            awaitEntryNotification("Create")
            entryNotification().x { byVisibleText("Create") }.click()
            waitFor("dialog with both captures reverse-captured", timeout = 2.minutes, errorMessage = { bridge.describeDialog() }) {
                val lines = bridge.describeDialog().lines()
                "dialog=open" in lines && "field=feature=home" in lines && "field=name=UserCard" in lines &&
                    lines.any { it.startsWith("target=") && it.endsWith(relative) }
            }
            println("SAMPLE-DIALOG:\n" + bridge.describeDialog())
            assertEquals(placeholder, project.resolve(relative).readText(), "Opening the dialog must not write")
            ui.dialog(title = "katachi: Generate from Template").x { byVisibleText("Generate") }.click()
            awaitEntryDialog(bridge, "dialog=none")
            waitFor("placeholder overwritten by the real katachiTemplate", timeout = 15.minutes, interval = 2.seconds) {
                project.resolve(relative).readText().contains("internal fun HomeUserCard(")
            }
            println("SAMPLE-RESULT:\n" + project.resolve(relative).readText())
        }
    }

    private fun run(dir: Path, vararg command: String) {
        val process = ProcessBuilder(*command).directory(dir.toFile()).inheritIO().start()
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("Timed out: ${command.first()}")
        }
        check(process.exitValue() == 0) { "Failed: ${command.joinToString(" ")}" }
    }
}
