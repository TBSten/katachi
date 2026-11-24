package me.tbsten.katachi.intellij.ide.script

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.data.script.KatachiCliLocator
import me.tbsten.katachi.intellij.data.script.ScriptClasspath
import me.tbsten.katachi.intellij.ide.KatachiBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * The ▶ of a `*.katachi.kts` and the run configuration behind it (KATACHI-100). The CLI is never the real one: the
 * answer the ▶ depends on is the test's, and the CLI the run would start is a path the test names.
 */
internal class KatachiScriptRunTest : AnalysisTestBase() {
    private val found = ScriptClasspath.Found(listOf(Path.of("/opt/katachi/katachi-script.jar")))

    private fun contributor(answer: ScriptClasspath?) = KatachiScriptRunLineMarkerContributor(answerOf = { answer })

    private fun script(name: String = "setup.katachi.kts", text: String = "// katachi\nval x = 1\n"): PsiFile = myFixture.addFileToProject(name, text)

    private fun firstLeaf(file: PsiFile) = PsiTreeUtil.firstChild(file)

    private fun leaves(file: PsiFile) = PsiTreeUtil.collectElements(file) { it.firstChild == null }.toList()

    private fun infoOf(file: PsiFile, answer: ScriptClasspath?): List<RunLineMarkerContributor.Info> =
        leaves(file).mapNotNull { contributor(answer).getInfo(it) }

    fun `test plugin xml が ▶ と実行構成と Run の元を 1 つずつ登録している`() {
        assertEquals(1, ConfigurationTypeUtil.findConfigurationType(KatachiScriptConfigurationType::class.java).configurationFactories.size)
        assertEquals(1, RunLineMarkerContributor.EXTENSION.allForLanguage(KotlinLanguage.INSTANCE).filterIsInstance<KatachiScriptRunLineMarkerContributor>().size)
        assertEquals(1, RunConfigurationProducer.EP_NAME.extensionList.filterIsInstance<KatachiScriptRunProducer>().size)
    }

    fun `test CLI が定義を渡した katachi kts の 1 行目の先頭に ▶ が 1 つだけ出る`() {
        val file = script()
        val infos = infoOf(file, found)
        assertEquals(1, infos.size)
        assertEquals(KatachiBundle.message("script.run.tooltip", "setup.katachi.kts"), infos.single().tooltipProvider.apply(firstLeaf(file)))
        assertNotNull(contributor(found).getInfo(firstLeaf(file)))
    }

    fun `test CLI が無い 古い 壊れた 答えを待っている のどれでも ▶ は出ない`() {
        val file = script()
        for (answer in listOf(null, ScriptClasspath.CliNotFound, ScriptClasspath.Unsupported("exit 1"), ScriptClasspath.Malformed("not a file"))) {
            assertEquals("$answer", emptyList<Any>(), infoOf(file, answer))
        }
    }

    fun `test katachi kts 以外のファイルには CLI があっても ▶ は出ない`() {
        for (name in listOf("build.gradle.kts", "katachi.main.kts", "Main.kt")) {
            assertEquals(name, emptyList<Any>(), infoOf(script(name), found))
        }
    }

    private inline fun <reified T : Throwable> failureOf(block: () -> Unit): T {
        val failure = runCatching(block).exceptionOrNull() ?: throw AssertionError("expected ${T::class.java.simpleName}, but nothing was thrown")
        return failure as? T ?: throw AssertionError("expected ${T::class.java.simpleName}, but was $failure")
    }

    private fun contextOf(file: PsiFile) = ConfigurationContext(file)

    fun `test Run は katachi kts のパスと名前を持った実行構成を作る`() {
        val file = script()
        val producer = KatachiScriptRunProducer(answerOf = { found })
        val fromContext = producer.createConfigurationFromContext(contextOf(file)) ?: throw AssertionError("no configuration")
        val configuration = fromContext.configuration as KatachiScriptRunConfiguration
        assertEquals(file.virtualFile.path, configuration.scriptPath)
        assertEquals("setup.katachi.kts", configuration.name)
        assertTrue(producer.isConfigurationFromContext(configuration, contextOf(file)))
        assertFalse(producer.isConfigurationFromContext(configuration, contextOf(script("other.katachi.kts"))))
    }

    fun `test CLI の答えが無いときと katachi kts 以外では Run の元は何も作らない`() {
        assertNull(KatachiScriptRunProducer(answerOf = { ScriptClasspath.CliNotFound }).createConfigurationFromContext(contextOf(script())))
        assertNull(KatachiScriptRunProducer(answerOf = { found }).createConfigurationFromContext(contextOf(script("build.gradle.kts"))))
    }

    private fun configuration(scriptPath: String): KatachiScriptRunConfiguration {
        val type = ConfigurationTypeUtil.findConfigurationType(KatachiScriptConfigurationType::class.java)
        val settings = RunManager.getInstance(project).createConfiguration("test", type.configurationFactories.single())
        return (settings.configuration as KatachiScriptRunConfiguration).also { it.scriptPath = scriptPath }
    }

    private fun locatorWith(vararg executables: Path) =
        KatachiCliLocator(pathVariable = "/fake/bin", home = null, windows = false, isExecutable = { it in executables })

    // The light fixture's files are in memory; the run checks the disk, as the CLI will.
    private fun scriptOnDisk(): String = createTempDirectory("katachi-run-").resolve("setup.katachi.kts").also { it.writeText("// katachi\n") }.toString()

    fun `test 実行構成は katachi に katachi kts を渡し プロジェクトの場所で PATH を付けて実行する`() {
        val path = scriptOnDisk()
        val commandLine = configuration(path).commandLine(locatorWith(Path.of("/fake/bin/katachi")))
        assertEquals(listOf("/fake/bin/katachi", path), listOf(commandLine.exePath) + commandLine.parametersList.list)
        assertEquals(project.basePath, commandLine.workDirectory?.path)
        assertTrue(commandLine.environment.getValue("PATH").split(':').contains("/fake/bin"))
    }

    fun `test CLI が見つからない実行は 入れ方を添えて始める前に失敗する`() {
        val failure = failureOf<ExecutionException> { configuration(scriptOnDisk()).commandLine(locatorWith()) }
        assertEquals(KatachiBundle.message("script.run.error.cliNotFound", KatachiScriptNotificationProvider.INSTALL_COMMAND), failure.message)
    }

    fun `test スクリプトが空 katachi kts でない 無い のどれでも実行構成は動かさず 理由を言う`() {
        val missing = "/no/such/dir/setup.katachi.kts"
        for ((path, expected) in listOf(
            "" to KatachiBundle.message("script.run.error.scriptPathEmpty"),
            "/p/build.gradle.kts" to KatachiBundle.message("script.run.error.notKatachiScript", "/p/build.gradle.kts"),
            missing to KatachiBundle.message("script.run.error.scriptMissing", missing),
        )) {
            val configuration = configuration(path)
            assertEquals(path, expected, failureOf<RuntimeConfigurationError> { configuration.checkConfiguration() }.localizedMessage)
            assertEquals(path, expected, failureOf<ExecutionException> { configuration.commandLine(locatorWith(Path.of("/fake/bin/katachi"))) }.message)
        }
    }

    fun `test 実行構成のスクリプトのパスは保存して読み戻せる`() {
        val original = configuration("/p/setup.katachi.kts")
        val element = org.jdom.Element("configuration")
        original.writeExternal(element)
        val copy = configuration("")
        copy.readExternal(element)
        assertEquals("/p/setup.katachi.kts", copy.scriptPath)
    }
}
