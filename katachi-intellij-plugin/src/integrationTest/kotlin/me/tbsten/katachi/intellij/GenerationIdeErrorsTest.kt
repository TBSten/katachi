package me.tbsten.katachi.intellij

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.openToolWindow
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** [KatachiDebugBridgeRef] with "uncheck all", to generate more than once. */
@Remote("me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge", plugin = "me.tbsten.katachi.intellij")
interface GenerationBridgeRef {
    fun describeState(): String

    fun checkAndFill(template: String, args: String): Boolean

    fun generate()

    fun uncheckAll()
}

/**
 * Generating from the tool window leaves no error in the IDE's log.
 *
 * Several templates generated at once run one Gradle build right after another. The platform
 * finishes a build's Run tab on the EDT after the build has ended (it folds the "... finished"
 * line), and a next build started too early reuses that tab and disposes its console first, which
 * the platform reports as an error (a NullPointerException in
 * `ExternalSystemRunConfiguration.foldGreetingOrFarewell`). The generation itself succeeds, so
 * only the IDE's log shows it.
 *
 * sample/jvm already has two templates (Service, Controller), so the copy it runs on gets one more
 * (on the Repository role, [addTemplates]) to make three, and the generation is repeated, as the
 * error depends on the order in which the EDT runs things.
 */
class GenerationIdeErrorsTest {
    private val sample: Path = Paths.get(System.getProperty("katachi.smoke.sampleProject"))
    private val ideHome: Path = Paths.get(System.getProperty("katachi.smoke.ideHome"))
    private val workDir: Path = Paths.get(System.getProperty("katachi.smoke.workDir")).resolve("ide-errors")

    @Test
    fun `複数のテンプレートを続けて生成してもIDEにエラーが出ない`() {
        keepStarterFilesUnder(workDir.resolve("starter"))
        val repository = copyOfRepository(sample, workDir.resolve("repository"))
        addTemplates(repository.resolve("sample/jvm"))
        val ide = IdeProductProvider.IU.copy(getInstaller = { ExistingIdeInstaller(appBundleOf(ideHome, workDir)) })
        val context = Starter.newContext("katachiGenerationIdeErrors", TestCase(ide, LocalProjectInfo(repository.resolve("sample/jvm"))), false)
            .apply { pluginConfigurator.installPluginFromPath(Paths.get(System.getProperty("path.to.build.plugin"))) }
            .applyVMOptionsPatch { addSystemProperty("katachi.debugBridge", true) }
        val testHome = context.paths.testHome

        context.runIdeWithDriver(runTimeout = 40.minutes).useDriverAndCloseIde {
            waitForProjectOpen(5.minutes)
            val ideProject = singleProject()
            waitForIndicators(ideProject, 15.minutes)

            openToolWindow("katachi")
            val bridge = service(GenerationBridgeRef::class, ideProject)
            waitFor("the templates", timeout = 10.minutes, interval = 2.seconds, errorMessage = { bridge.describeState() }) {
                val state = bridge.describeState()
                TEMPLATES.all { "template=$it" in state }
            }
            val errorsBefore = ideErrorsUnder(testHome)

            repeat(ROUNDS) { round ->
                TEMPLATES.forEach { assertTrue(bridge.checkAndFill(it, argsOf(it, round)), bridge.describeState()) }
                bridge.generate()
                waitFor("generation $round to finish", timeout = 10.minutes, interval = 1.seconds, errorMessage = { bridge.describeState() }) {
                    "generation=finished" in bridge.describeState()
                }
                val state = bridge.describeState()
                assertTrue(TEMPLATES.all { "result=$it:Generated" in state }, state)
                // Leaves the result, so that the next round checks the rows again.
                bridge.uncheckAll()
            }
            // What the platform leaves on the EDT after the last build (the Run tab's folding) runs by then.
            Thread.sleep(5_000)

            val newErrors = ideErrorsUnder(testHome).drop(errorsBefore.size)
                .filterNot { entry -> ERROR_CONTEXT.any { it in entry.lineSequence().first() } }
                .filterNot { entry -> UNRELATED_ERRORS.any { it in entry } }
            assertEquals(emptyList<String>(), newErrors, "The IDE logged errors while generating:\n" + newErrors.joinToString("\n\n"))
        }
    }

    /**
     * Gives the sample's Repository role a `.template { }` of its own -- of these three, the only
     * one with no template yet (Service and Controller already have one) -- so that one Generate
     * runs three builds in a row: `.template(id, title) { }` attaches to one of the role's own
     * `layout { }` file declarations (design draft section 1), so this swaps the role's one
     * unnamed-wildcard file declaration for the same file named with `capture("name")` and templated.
     */
    private fun addTemplates(sampleJvm: Path) {
        val role = sampleJvm.resolve("architecture-test/src/test/kotlin/com/example/roles/RepositoryRole.kt")
        val existing = "\"*Repository\".ktFile()"
        val source = role.readText()
        check(existing in source) { "$role no longer declares the Repository file the way this test expects" }
        val replacement = """
            |"${'$'}{capture("name")}Repository".ktFile().template {
            |                val name = captureValue("name")
            |                "package com.example.repository\n\nclass ${'$'}{name}Repository\n"
            |            }
        """.trimMargin()
        // `template` is an extension function, so the role needs its import to compile.
        val anchor = "import me.tbsten.katachi.dsl.kotlin.ktFile\n"
        check(anchor in source) { "$role no longer imports ktFile the way this test expects" }
        val withImport = if (TEMPLATE_IMPORT in source) source else source.replace(anchor, anchor + TEMPLATE_IMPORT)
        // `template` and `capture` are ExperimentalKatachiApi, so a role that does not opt in yet needs both lines.
        val withOptIn = if (OPT_IN in withImport) {
            withImport
        } else {
            OPT_IN + "\n\n" + withImport.replace(anchor, anchor + EXPERIMENTAL_IMPORT)
        }
        role.writeText(withOptIn.replace(existing, replacement))
    }

    /** The fields each template asks for: the Controller's package level is `capture("resource")` as well. */
    private fun argsOf(template: String, round: Int): String = when (template) {
        CONTROLLER_TEMPLATE -> "name=ErrorProbe$round\nresource=probe$round"
        else -> "name=ErrorProbe$round"
    }

    private companion object {
        const val TEMPLATE_IMPORT = "import me.tbsten.katachi.dsl.template\n"
        const val OPT_IN = "@file:OptIn(ExperimentalKatachiApi::class)"
        const val EXPERIMENTAL_IMPORT = "import me.tbsten.katachi.ExperimentalKatachiApi\n"
        const val SERVICE_TEMPLATE = "domain.Service"
        const val REPOSITORY_TEMPLATE = "data.Repository"
        const val CONTROLLER_TEMPLATE = "api.Controller"
        val TEMPLATES = listOf(SERVICE_TEMPLATE, REPOSITORY_TEMPLATE, CONTROLLER_TEMPLATE)

        /** The error depends on how the EDT orders things, so the builds run back to back many times. */
        const val ROUNDS = 8

        /**
         * Errors the IDE logs that have nothing to do with katachi: the Performance Testing plugin
         * Starter installs fails on the headless graphics of the test IDE.
         */
        val UNRELATED_ERRORS = listOf("Unsupported graphics configuration")

        /** The lines the IDE logs after an error, describing the IDE rather than an error. */
        val ERROR_CONTEXT = listOf("SEVERE - #c.i.o.a.i.ExceptionsKt - ", "SEVERE - #c.i.i.p.PluginManager - ")
            .flatMap { logger -> listOf("IntelliJ IDEA", "JDK:", "OS:", "Last Action:", "Plugin to blame:").map { logger + it } }
    }
}

// TODO: the next four are copies of ToolWindowSmokeTest's private helpers; share them once that
//  file can be edited (a shared file needs the `*Test` naming of the IdePluginTest role relaxed).

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
 * `.app` directory, so the bundle is rebuilt around a copy of it under [workDir]. A copy and not a
 * symbolic link: macOS kills (exit 137) a signed launcher whose bundle is a link to elsewhere.
 */
private fun appBundleOf(contents: Path, workDir: Path): Path {
    val bundle = workDir.resolve("ide/IntelliJ IDEA.app")
    if (!Files.exists(bundle.resolve("Contents/Info.plist"))) {
        runCommand(workDir, "rm", "-rf", bundle.toString())
        Files.createDirectories(bundle)
        runCommand(workDir, "ditto", contents.toString(), bundle.resolve("Contents").toString())
    }
    return bundle
}

/**
 * Copies the katachi repository that holds [sample] into [destination] without build output, so
 * that the IDE opens the copy (sample/jvm includes the whole repository). `git init` keeps
 * katachi's `gitTracked()` working in the copy. Returns [destination].
 */
private fun copyOfRepository(sample: Path, destination: Path): Path {
    val repository = sample.resolve("../..").normalize()
    runCommand(destination.parent, "rm", "-rf", destination.toString())
    Files.createDirectories(destination)
    runCommand(destination.parent, "rsync", "-a", *REPOSITORY_COPY_EXCLUDES, "$repository/", "$destination/")
    runCommand(destination, "git", "init", "-q")
    return destination
}

private fun runCommand(directory: Path, vararg command: String) {
    Files.createDirectories(directory)
    val process = ProcessBuilder(*command).directory(directory.toFile()).inheritIO().start()
    check(process.waitFor(5, TimeUnit.MINUTES) && process.exitValue() == 0) { "${command.joinToString(" ")} failed" }
}

/**
 * The errors the IDE logged under [testHome]: every `SEVERE` entry of each `idea.log` (the level
 * `Logger.error` writes, which the IDE also shows as a red error), with the lines that follow it up
 * to the next entry, so that the stack trace comes along.
 */
private fun ideErrorsUnder(testHome: Path): List<String> {
    val logs = Files.walk(testHome).use { paths -> paths.filter { it.name == "idea.log" }.toList() }
    return logs.flatMap { log -> severeEntriesOf(log.readLines()) }
}

private val LOG_ENTRY_START = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3} """)

private fun severeEntriesOf(lines: List<String>): List<String> {
    val entries = mutableListOf<String>()
    var current: StringBuilder? = null
    for (line in lines) {
        if (LOG_ENTRY_START.containsMatchIn(line)) {
            current?.let { entries += it.toString() }
            current = if (" SEVERE - " in line) StringBuilder(line) else null
        } else {
            current?.append('\n')?.append(line)
        }
    }
    current?.let { entries += it.toString() }
    return entries
}
