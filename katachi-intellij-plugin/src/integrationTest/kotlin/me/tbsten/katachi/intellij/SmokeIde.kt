package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
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
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * What the new real-IDE smokes share (quality design 2.4): a fresh copy of sample/jvm that is a git repository with one commit,
 * the IDE's `.app` rebuilt around the one the build resolved, Starter's files under the build directory, the built plugin
 * installed, the debug bridge on and English text, up to a Driver with the project open and indexed.
 *
 * ```kotlin
 * val ide = SmokeIde("gutter-surfaces")
 * val project = ide.prepareCopy { project -> project.resolve("src/Probe.kt").writeText("...") }
 * ide.run(project) { ideProject, bridge -> bridge.describeGutter(path) }
 * ```
 *
 * The older smokes (`GutterSmokeTest`, `NoRoleSmokeTest`, `EditorEntry*`) keep their own copies of this: they run in the nightly 263
 * job and stay green, so they are not moved here.
 */
// TODO(smoke-helper): move the older smokes onto SmokeIde when #14 (the smoke helpers) is looked at again.
@Suppress("NewApi")
internal class SmokeIde(name: String) {
    private val sample: Path = Paths.get(System.getProperty("katachi.smoke.sampleProject"))
    private val ideHome: Path = Paths.get(System.getProperty("katachi.smoke.ideHome"))
    private val rootDir: Path = Paths.get(System.getProperty("katachi.smoke.workDir"))

    /** This smoke's directory under the integration test's work directory. */
    val workDir: Path = rootDir.resolve(name)

    /** Where the smoke's screenshots go. */
    val screenshots: Path = workDir.resolve("screenshots")

    /**
     * Copies the repository (sample/jvm includes the whole of it) into a new directory, drops what earlier runs left, makes it
     * a git repository with one commit (`gitTracked()` would find nothing otherwise), and returns the copy's `sample/jvm`.
     * [edit] plants the files and edits the settings the smoke needs, before the commit.
     */
    fun prepareCopy(edit: (project: Path) -> Unit = {}): Path {
        Files.createDirectories(workDir)
        Files.newDirectoryStream(workDir).use { entries ->
            entries.filter { it.fileName.toString().let { n -> n.startsWith("ide-") || n.startsWith("repository-") } }
                .forEach { it.toFile().deleteRecursively() }
        }
        keepStarterFilesUnder(workDir.resolve("starter"))
        val repository = Files.createTempDirectory(workDir, "repository-")
        exec(workDir, "rsync", "-a", *REPOSITORY_COPY_EXCLUDES, "${sample.resolve("../..").normalize()}/", "$repository/")
        val project = repository.resolve("sample/jvm")
        edit(project)
        exec(repository, "git", "init", "-q")
        exec(repository, "git", "add", "-A")
        exec(repository, "git", "-c", "user.name=katachi-smoke", "-c", "user.email=smoke@example.com", "commit", "-q", "-m", "baseline")
        return project
    }

    /**
     * Starts the IDE on [project], waits for it to open and index, and runs [block] with Driver, the project and the bridge.
     * The IDE is closed after [block], thrown or not. The first Gradle import and the definition task take minutes.
     */
    fun run(
        project: Path,
        runTimeout: Duration = 40.minutes,
        block: Driver.(ideProject: Project, bridge: SmokeBridge) -> Unit,
    ) {
        val ide = IdeProductProvider.IU.copy(getInstaller = { ExistingIdeInstaller(appBundle()) })
        val context = Starter.newContext("katachi-${workDir.fileName}", TestCase(ide, LocalProjectInfo(project)), false)
            .apply { pluginConfigurator.installPluginFromPath(Paths.get(System.getProperty("path.to.build.plugin"))) }
            .applyVMOptionsPatch {
                addSystemProperty("katachi.debugBridge", true)
                // The texts the smokes assert are English.
                addSystemProperty("user.language", "en")
                addSystemProperty("user.country", "US")
            }
        context.runIdeWithDriver(runTimeout = runTimeout).useDriverAndCloseIde {
            waitForProjectOpen(5.minutes)
            val ideProject = singleProject()
            waitForIndicators(ideProject, 15.minutes)
            block(ideProject, service(SmokeBridge::class, ideProject))
        }
    }

    /** Every file under [project] but the IDE's and Gradle's own state, by relative path, so a smoke can say "nothing was added". */
    fun sourcesOf(project: Path): Set<String> = Files.walk(project).use { paths ->
        paths.filter { Files.isRegularFile(it) && project.relativize(it).none { part -> part.toString() in STATE_DIRECTORIES } }
            .map { project.relativize(it).toString() }
            .toList()
            .toSet()
    }

    /** Starter puts its IDE copies, configs and logs under the git root by default; keep them under the build directory. */
    private fun keepStarterFilesUnder(directory: Path) {
        Files.createDirectories(directory)
        di = DI {
            extend(di)
            bindSingleton<GlobalPaths>(overrides = true) { object : GlobalPaths(directory) {} }
        }
    }

    /** The IDE the build resolved is the `Contents` of a macOS bundle and Starter only takes a `.app`: rebuilt around a copy of it. */
    private fun appBundle(): Path {
        val bundle = Files.createTempDirectory(workDir, "ide-").resolve("IntelliJ IDEA.app")
        Files.createDirectories(bundle)
        exec(workDir, "ditto", ideHome.toString(), bundle.resolve("Contents").toString())
        // The Gradle plugin adds this marker, which breaks the bundle's code seal; macOS then kills the 263 JBR with SIGKILL.
        Files.deleteIfExists(bundle.resolve("Contents/Resources/.toolbox-ignore"))
        return bundle
    }

    private fun exec(directory: Path, vararg command: String) {
        Files.createDirectories(directory)
        val process = ProcessBuilder(*command).directory(directory.toFile()).inheritIO().start()
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("Timed out: ${command.joinToString(" ")}")
        }
        check(process.exitValue() == 0) { "Failed: ${command.joinToString(" ")}" }
    }

    private companion object {
        val STATE_DIRECTORIES = setOf(".idea", ".gradle", ".kotlin", "build", ".git")
    }
}
