package me.tbsten.katachi.intellij.ide.script

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import me.tbsten.katachi.intellij.data.script.ScriptClasspath
import me.tbsten.katachi.intellij.data.script.ScriptRun

/**
 * Run in the context menu of a `*.katachi.kts`, and the configuration the ▶ in its gutter runs. Offered only where the
 * ▶ is: when the installed CLI has given the script's definition.
 */
internal class KatachiScriptRunProducer(
    private val answerOf: () -> ScriptClasspath? = { KatachiScriptService.getInstance().current() },
) : LazyRunConfigurationProducer<KatachiScriptRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(KatachiScriptConfigurationType::class.java).configurationFactories.single()

    override fun setupConfigurationFromContext(
        configuration: KatachiScriptRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val script = scriptOf(context) ?: return false
        configuration.scriptPath = script.path
        configuration.name = script.name
        return true
    }

    override fun isConfigurationFromContext(configuration: KatachiScriptRunConfiguration, context: ConfigurationContext): Boolean =
        scriptOf(context)?.path == configuration.scriptPath

    private fun scriptOf(context: ConfigurationContext): VirtualFile? {
        val file = context.psiLocation?.containingFile?.originalFile?.virtualFile ?: return null
        return file.takeIf { ScriptRun.runnable(it.name, answerOf()) }
    }
}
