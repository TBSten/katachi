package me.tbsten.katachi.intellij.ide.script

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import me.tbsten.katachi.intellij.data.script.ScriptClasspath
import me.tbsten.katachi.intellij.data.script.ScriptRun
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.sdkCall

/**
 * The ▶ in the gutter of the first line of a `*.katachi.kts` (KATACHI-100): it runs the script with the installed katachi
 * CLI, and its output goes to the Run tool window. Not shown on other files, nor while the CLI has not given the
 * script's definition (none installed, too old, or not answered yet): the banner of [KatachiScriptNotificationProvider]
 * says what to do then, and ▶ appears once the CLI is checked again.
 */
internal class KatachiScriptRunLineMarkerContributor(
    private val answerOf: () -> ScriptClasspath? = { KatachiScriptService.getInstance().current() },
) : RunLineMarkerContributor(), DumbAware {
    override fun getInfo(element: PsiElement): Info? {
        // Leaf elements only, as the platform asks of a contributor; and one marker per file, on its first leaf.
        if (element.firstChild != null) return null
        val file = element.containingFile ?: return null
        if (!ScriptRun.runnable(file.name, answerOf())) return null
        if (PsiTreeUtil.firstChild(file) !== element) return null
        val actions = sdkCall("list the run actions of ${file.name}") {
            ExecutorAction.getActions(0).filter { it is ExecutorAction && it.executor.id == DefaultRunExecutor.EXECUTOR_ID }
        }.getOrDefault(emptyList())
        return Info(AllIcons.RunConfigurations.TestState.Run, actions.toTypedArray()) { KatachiBundle.message("script.run.tooltip", file.name) }
    }
}
