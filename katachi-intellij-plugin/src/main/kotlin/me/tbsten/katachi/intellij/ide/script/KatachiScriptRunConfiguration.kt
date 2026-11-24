package me.tbsten.katachi.intellij.ide.script

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.util.ui.FormBuilder
import me.tbsten.katachi.intellij.data.script.KatachiCliLocator
import me.tbsten.katachi.intellij.data.script.ScriptRun
import me.tbsten.katachi.intellij.data.script.isKatachiScript
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.gutter.KatachiIcons
import java.io.File
import java.nio.charset.StandardCharsets
import javax.swing.JComponent

/**
 * "katachi Script": runs a `*.katachi.kts` with the installed katachi CLI, the way `katachi <file>.katachi.kts` does in a
 * terminal (KATACHI-100). The ▶ in the gutter of the script ([KatachiScriptRunLineMarkerContributor]) and Run in its
 * context menu ([KatachiScriptRunProducer]) both make one of these; it is also saved in Run | Edit Configurations.
 */
internal class KatachiScriptConfigurationType : SimpleConfigurationType(
    ID,
    KatachiBundle.message("script.run.type.name"),
    KatachiBundle.message("script.run.type.description"),
    NotNullLazyValue.createValue { KatachiIcons.tool },
) {
    override fun createTemplateConfiguration(project: Project): KatachiScriptRunConfiguration =
        KatachiScriptRunConfiguration(project, this, KatachiBundle.message("script.run.type.name"))

    override fun getOptionsClass(): Class<out BaseState> = KatachiScriptOptions::class.java

    companion object {
        const val ID: String = "me.tbsten.katachi.intellij.script"
    }
}

/** What a run configuration keeps in `.idea/runConfigurations` or the workspace: the script to run. */
internal class KatachiScriptOptions : RunConfigurationOptions() {
    private val scriptPathProperty = string("").provideDelegate(this, "scriptPath")

    var scriptPath: String
        get() = scriptPathProperty.getValue(this).orEmpty()
        set(value) = scriptPathProperty.setValue(this, value)
}

internal class KatachiScriptRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String?,
) : RunConfigurationBase<KatachiScriptOptions>(project, factory, name) {
    override fun getOptions(): KatachiScriptOptions = super.getOptions() as KatachiScriptOptions

    var scriptPath: String
        get() = options.scriptPath
        set(value) {
            options.scriptPath = value
        }

    override fun getConfigurationEditor(): SettingsEditor<KatachiScriptRunConfiguration> = KatachiScriptSettingsEditor(project)

    override fun checkConfiguration() {
        val problem = ScriptRunProblem.of(scriptPath, exists = { File(it).isFile }) ?: return
        throw RuntimeConfigurationError(problem.text())
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = object : CommandLineState(environment) {
        override fun startProcess(): ProcessHandler =
            KillableColoredProcessHandler(commandLine()).also { ProcessTerminatedListener.attach(it) }
    }

    /** What the run starts: `katachi <script>` in the project's directory. Fails, before anything starts, with the reason it cannot. */
    internal fun commandLine(locator: KatachiCliLocator = KatachiCliEnvironment.locator()): GeneralCommandLine {
        val script = scriptPath
        ScriptRunProblem.of(script, exists = { File(it).isFile })?.let { throw ExecutionException(it.text()) }
        val cli = locator.locate()
            ?: throw ExecutionException(KatachiBundle.message("script.run.error.cliNotFound", KatachiScriptNotificationProvider.INSTALL_COMMAND))
        val commandLine = GeneralCommandLine(ScriptRun.command(cli, script))
            .withCharset(StandardCharsets.UTF_8)
            // `katachi` runs `kotlin`, which an IDE started from the Dock cannot find on its own PATH either.
            .withEnvironment("PATH", locator.searchPath())
        ScriptRun.workingDirectory(project.basePath, script)?.let { commandLine.withWorkDirectory(it) }
        return commandLine
    }
}

/** Why a script path cannot be run, before the CLI is asked. */
internal sealed interface ScriptRunProblem {
    fun text(): String

    data object Empty : ScriptRunProblem {
        override fun text(): String = KatachiBundle.message("script.run.error.scriptPathEmpty")
    }

    data class Missing(val path: String) : ScriptRunProblem {
        override fun text(): String = KatachiBundle.message("script.run.error.scriptMissing", path)
    }

    data class NotKatachiScript(val path: String) : ScriptRunProblem {
        override fun text(): String = KatachiBundle.message("script.run.error.notKatachiScript", path)
    }

    companion object {
        fun of(scriptPath: String, exists: (String) -> Boolean): ScriptRunProblem? = when {
            scriptPath.isBlank() -> Empty
            !isKatachiScript(scriptPath) -> NotKatachiScript(scriptPath)
            !exists(scriptPath) -> Missing(scriptPath)
            else -> null
        }
    }
}

private class KatachiScriptSettingsEditor(private val project: Project) : SettingsEditor<KatachiScriptRunConfiguration>() {
    private val scriptField = TextFieldWithBrowseButton().also {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withTitle(KatachiBundle.message("script.run.scriptPath.title"))
            .withFileFilter { file -> isKatachiScript(file.name) }
        it.addBrowseFolderListener(project, descriptor)
    }

    override fun resetEditorFrom(configuration: KatachiScriptRunConfiguration) {
        scriptField.text = configuration.scriptPath
    }

    override fun applyEditorTo(configuration: KatachiScriptRunConfiguration) {
        configuration.scriptPath = scriptField.text.trim()
    }

    override fun createEditor(): JComponent =
        FormBuilder.createFormBuilder().addLabeledComponent(KatachiBundle.message("script.run.scriptPath"), scriptField).panel
}
