package me.tbsten.katachi.intellij.ide.script

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotifications
import com.intellij.util.EnvironmentUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.data.script.KatachiCliLocator
import me.tbsten.katachi.intellij.data.script.ScriptClasspath
import me.tbsten.katachi.intellij.data.script.ScriptClasspathProbe
import me.tbsten.katachi.intellij.data.script.ScriptClasspathState
import me.tbsten.katachi.intellij.data.script.TimedProcessRunner
import me.tbsten.katachi.intellij.data.script.isKatachiScript
import me.tbsten.katachi.intellij.ide.sdkCall
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files

/**
 * What the installed katachi CLI says about `*.katachi.kts`, asked once per IDE session in the background
 * (KATACHI-101). The plugin does not bundle katachi: the jars of the script definition come from the CLI, so they
 * follow the katachi the user has, not the plugin's version. Without a CLI nothing else of the plugin changes.
 */
@Service(Service.Level.APP)
internal class KatachiScriptService(private val scope: CoroutineScope) {
    @Volatile
    private var state: ScriptClasspathState = ScriptClasspathState(
        probe = ::probeInstalledCli,
        inBackground = { block -> scope.launch(Dispatchers.IO) { block() } },
        // Never on the caller's thread: markUnusable is called while the Kotlin plugin computes the definitions.
        onChange = { before, after -> scope.launch(Dispatchers.IO) { changed(before, after) } },
    )

    /** Null until the CLI has answered; asking starts it. */
    fun current(): ScriptClasspath? = state.current()

    fun recheck() = state.recheck()

    /** The jars were there but held no definition, so the answer is no better than a broken one. */
    fun markUnusable(found: ScriptClasspath.Found, detail: String) = state.replace(found, ScriptClasspath.Malformed(detail))

    /** Answers with [probe], run on the calling thread, until [parent] is disposed. */
    @TestOnly
    fun useProbe(parent: Disposable, onChange: (ScriptClasspath?, ScriptClasspath) -> Unit = ::changed, probe: () -> ScriptClasspath) {
        val before = state
        state = ScriptClasspathState(probe, inBackground = { it() }, onChange = onChange)
        Disposer.register(parent) { state = before }
    }

    private fun changed(before: ScriptClasspath?, after: ScriptClasspath) {
        // The definitions are loaded again only when there were or will be some: a CLI that is still missing
        // changes nothing but the banner of the open scripts.
        val definitionsChanged = before is ScriptClasspath.Found || after is ScriptClasspath.Found
        val projects = sdkCall("list the open projects") { ProjectManager.getInstance().openProjects.toList() }.getOrDefault(emptyList())
        for (project in projects) {
            if (project.isDisposed) continue
            if (definitionsChanged) ScriptDefinitionsReload.reload(project)
            refreshOpenScripts(project, restartHighlighting = definitionsChanged)
        }
    }

    private fun refreshOpenScripts(project: Project, restartHighlighting: Boolean) {
        val scripts = sdkCall("list the open katachi scripts") {
            FileEditorManager.getInstance(project).openFiles.filter { isKatachiScript(it.name) }
        }.getOrDefault(emptyList())
        for (file in scripts) {
            sdkCall("update the notifications of ${file.path}") { if (!project.isDisposed) EditorNotifications.getInstance(project).updateNotifications(file) }
            if (!restartHighlighting) continue
            sdkCall("highlight ${file.path} again") {
                runReadActionBlocking {
                    if (!project.isDisposed) {
                        PsiManager.getInstance(project).findFile(file)?.let { DaemonCodeAnalyzer.getInstance(project).restart(it, "katachi: script definition changed") }
                    }
                }
            }
        }
    }

    companion object {
        fun getInstance(): KatachiScriptService = ApplicationManager.getApplication().getService(KatachiScriptService::class.java)

        private fun probeInstalledCli(): ScriptClasspath {
            val path = sdkCall("read the PATH of the login shell") { EnvironmentUtil.getValue("PATH") }.getOrNull() ?: System.getenv("PATH")
            val locator = KatachiCliLocator(
                pathVariable = path,
                home = System.getProperty("user.home"),
                windows = SystemInfo.isWindows,
                isExecutable = { Files.isRegularFile(it) && Files.isExecutable(it) },
            )
            return ScriptClasspathProbe(locate = locator::locate, run = TimedProcessRunner(), exists = Files::exists).probe()
        }
    }
}
