package me.tbsten.katachi.intellij.ide.script

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.data.script.KATACHI_SCRIPT_DEFINITION_MARKER
import me.tbsten.katachi.intellij.data.script.ScriptClasspath
import me.tbsten.katachi.intellij.ide.KatachiBundle
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

/**
 * `*.katachi.kts` in the IDE (KATACHI-101) when the katachi CLI cannot give the definition: none installed, one too
 * old, or an answer that is broken. In each case the Kotlin plugin gets no jar and no exception, and the
 * script's editor gets a banner; no other file does. The rest of the plugin never asks the CLI, so the other tests,
 * which run where no katachi is installed, are the check that it works without one.
 */
internal class KatachiScriptSupportTest : AnalysisTestBase() {
    private var answer: ScriptClasspath = ScriptClasspath.CliNotFound
    private val changes: MutableList<Pair<ScriptClasspath?, ScriptClasspath>> = java.util.Collections.synchronizedList(mutableListOf())
    private val copied = mutableListOf<String>()
    private var rechecks = 0
    private lateinit var service: KatachiScriptService

    override fun setUp() {
        super.setUp()
        service = KatachiScriptService.getInstance()
        // Never the real CLI: the answer is the test's.
        service.useProbe(testRootDisposable, onChange = { before, after -> changes += before to after }) { answer }
    }

    private val providers: List<ScriptDefinitionsProvider>
        get() = project.extensionArea.getExtensionPoint(ScriptDefinitionsProvider.EP_NAME).extensionList

    fun `test plugin xml が定義の提供元と通知を 1 つずつ登録している`() {
        assertEquals(1, providers.filterIsInstance<KatachiScriptDefinitionsProvider>().size)
        assertEquals(1, EditorNotificationProvider.EP_NAME.getExtensions(project).filterIsInstance<KatachiScriptNotificationProvider>().size)
    }

    fun `test CLI が無いとき 登録された提供元は jar を 1 つも渡さず例外も出さない`() {
        answer = ScriptClasspath.CliNotFound
        assertEquals(emptyList<Any>(), providers.filterIsInstance<KatachiScriptDefinitionsProvider>().single().getDefinitionsClassPath().toList())
    }

    fun `test 答えの前と 無い 古い 壊れた CLI のときは jar を渡さない`() {
        for (answer in listOf(null, ScriptClasspath.CliNotFound, ScriptClasspath.Unsupported("exit 1"), ScriptClasspath.Malformed("katachi 0.3.0"))) {
            assertEquals("$answer", emptyList<Any>(), KatachiScriptDefinitionsProvider.classpathOf(answer) { service })
        }
    }

    fun `test 定義の入った jar なら そのまま Kotlin プラグインに渡す`() {
        val jar = jarWith(KATACHI_SCRIPT_DEFINITION_MARKER)
        val found = ScriptClasspath.Found(listOf(jar))
        answer = found
        assertEquals(found, service.current())
        assertEquals(listOf(jar.toFile()), KatachiScriptDefinitionsProvider.classpathOf(found) { service })
        assertEquals(found, service.current())
    }

    fun `test jar でないファイルを指す答えでも 例外にせず jar を渡さず 壊れた答えとして扱う`() {
        val notJar = createTempDirectory("katachi-cli-").resolve("katachi-script.jar").also { it.writeText("not a jar") }
        assertFoundIsUnusable(ScriptClasspath.Found(listOf(notJar)))
    }

    fun `test katachi の定義の目印が無い jar でも 例外にせず jar を渡さず 壊れた答えとして扱う`() {
        assertFoundIsUnusable(ScriptClasspath.Found(listOf(jarWith("META-INF/kotlin/script/templates/com.example.Other.classname"))))
    }

    private fun jarWith(entry: String): Path {
        val jar = createTempDirectory("katachi-cli-").resolve("katachi-script.jar")
        JarOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(ZipEntry(entry))
            out.closeEntry()
        }
        return jar
    }

    private fun assertFoundIsUnusable(found: ScriptClasspath.Found) {
        answer = found
        assertEquals(found, service.current())
        assertEquals(emptyList<Any>(), KatachiScriptDefinitionsProvider.classpathOf(found) { service })
        val after = service.current()
        assertTrue("the banner tells: $after", after is ScriptClasspath.Malformed)
        assertEquals(found, changes.last().first)
    }

    fun `test 定義を読み直させても例外にならない`() {
        assertTrue("the Kotlin plugin's definitions are reloaded", ScriptDefinitionsReload.reload(project))
    }

    private fun provider(answer: ScriptClasspath?) =
        KatachiScriptNotificationProvider(answerOf = { answer }, recheck = { rechecks++ }, copy = { copied += it })

    // Any editor holds the banner; the provider reads only the name of the file it is asked about.
    private val editor: FileEditor by lazy {
        val other = myFixture.addFileToProject("notes.txt", "notes\n").virtualFile
        FileEditorManager.getInstance(project).openFile(other, true).single()
    }

    private fun panelOf(fileName: String, answer: ScriptClasspath?): EditorNotificationPanel? {
        val component = provider(answer).collectNotificationData(project, LightVirtualFile(fileName))?.apply(editor) ?: return null
        assertTrue("$component", component is EditorNotificationPanel)
        return component as? EditorNotificationPanel
    }

    private val EditorNotificationPanel.links: List<HyperlinkLabel> get() = UIUtil.findComponentsOfType(this, HyperlinkLabel::class.java)

    fun `test CLI が無ければ katachi kts に 入れ方のコピーと もう一度確かめるが出る`() {
        val panel = panelOf("setup.katachi.kts", ScriptClasspath.CliNotFound) ?: throw AssertionError("no banner")
        assertEquals(KatachiBundle.message("script.notification.cliNotFound"), panel.text)
        assertEquals(
            listOf(KatachiBundle.message("script.notification.copy", KatachiScriptNotificationProvider.INSTALL_COMMAND), KatachiBundle.message("script.notification.recheck")),
            panel.links.map { it.text },
        )
        panel.links[0].doClick()
        panel.links[1].doClick()
        assertEquals(listOf(KatachiScriptNotificationProvider.INSTALL_COMMAND), copied)
        assertEquals(1, rechecks)
    }

    fun `test 古い CLI と壊れた答えには 更新のコピーが出る`() {
        val old = panelOf("setup.katachi.kts", ScriptClasspath.Unsupported("exit 1")) ?: throw AssertionError("no banner")
        assertEquals(KatachiBundle.message("script.notification.unsupported"), old.text)
        val broken = panelOf("setup.katachi.kts", ScriptClasspath.Malformed("not a file: katachi 0.3.0")) ?: throw AssertionError("no banner")
        assertEquals(KatachiBundle.message("script.notification.malformed", "not a file: katachi 0.3.0"), broken.text)
        for (panel in listOf(old, broken)) {
            assertEquals(KatachiBundle.message("script.notification.copy", KatachiScriptNotificationProvider.UPGRADE_COMMAND), panel.links.first().text)
        }
    }

    fun `test 定義が取れたときと CLI の答えを待っているあいだは出ない`() {
        assertNull(panelOf("setup.katachi.kts", ScriptClasspath.Found(listOf(Path.of("/opt/katachi/katachi-script.jar")))))
        assertNull(panelOf("setup.katachi.kts", null))
    }

    fun `test CLI が無くても katachi kts 以外のファイルには出ない`() {
        for (name in listOf("build.gradle.kts", "katachi.main.kts", "Main.kt", "notes.txt")) {
            assertNull(name, panelOf(name, ScriptClasspath.CliNotFound))
        }
    }
}
