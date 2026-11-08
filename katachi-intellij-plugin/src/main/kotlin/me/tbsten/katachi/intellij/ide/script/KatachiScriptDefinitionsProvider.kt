package me.tbsten.katachi.intellij.ide.script

import com.intellij.openapi.diagnostic.Logger
import me.tbsten.katachi.intellij.data.script.ScriptClasspath
import me.tbsten.katachi.intellij.data.script.definitionJarsProblem
import me.tbsten.katachi.intellij.ide.sdkCall
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import java.io.File
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

/**
 * The `*.katachi.kts` definition for the Kotlin plugin: the jars `katachi internal script-classpath` prints, which the
 * Kotlin plugin scans for the definition's marker as it does its own `*.main.kts` jar, and puts ahead of the definition
 * of every other `*.kts`. It is what Settings > Languages & Frameworks > Kotlin > Kotlin Scripting lists. Without a
 * usable answer from the CLI it names no jar, and nothing it does throws: the Kotlin plugin asks every provider at once,
 * so a failure here would cost every other script its definition.
 */
internal class KatachiScriptDefinitionsProvider : ScriptDefinitionsProvider {
    override val id: String = "katachi"

    override fun getDefinitionClasses(): Iterable<String> = emptyList()

    /**
     * Abstract on 261 and 262; deprecated on 263 for `getTemplateClasspath()`, whose default calls this.
     *
     * verifyPlugin reports this override on 263, allowed by verifyPlugin-allowed-deprecated-usages.txt. Once sinceBuild is 263
     * or up, override `getTemplateClasspath(): Iterable<Path>` instead, remove this, and put `DEPRECATED_API_USAGES` back into
     * `pluginVerification.failureLevel` (build.gradle.kts).
     */
    override fun getDefinitionsClassPath(): Iterable<File> = classpathOf(KatachiScriptService.getInstance().current())

    override fun useDiscovery(): Boolean = true

    override fun provideDefinitions(baseHostConfiguration: ScriptingHostConfiguration, loadedScriptDefinitions: List<ScriptDefinition>): Iterable<ScriptDefinition> =
        guarded("add the standard library to the *.katachi.kts definition") { loadedScriptDefinitions.map(::withStandardLibrary) } ?: emptyList()

    internal companion object {
        private val LOG = Logger.getInstance(KatachiScriptDefinitionsProvider::class.java)

        /** The jars to scan, or none: a [ScriptClasspath.Found] whose jars cannot hold the definition is turned into a broken answer. */
        fun classpathOf(answer: ScriptClasspath?, service: () -> KatachiScriptService = { KatachiScriptService.getInstance() }): List<File> {
            val found = when (answer) {
                is ScriptClasspath.Found -> answer
                else -> return emptyList()
            }
            val problem = try {
                definitionJarsProblem(found.jars)
            } catch (failure: RuntimeException) {
                "${failure::class.simpleName}: ${failure.message}"
            } ?: return found.jars.map { it.toFile() }
            LOG.warn("The katachi CLI's jars cannot be used for *.katachi.kts: $problem")
            service().markUnusable(found, problem)
            return emptyList()
        }

        // The definition names the standard library of the class loader that read it, which inside the IDE may not be a jar;
        // the Kotlin plugin's own is added, as for `*.main.kts`.
        private fun withStandardLibrary(loaded: ScriptDefinition): ScriptDefinition = ScriptDefinition(
            loaded.compilationConfiguration.with { jvm { updateClasspath(listOf(KotlinArtifacts.kotlinStdlibPath.toFile())) } },
            loaded.evaluationConfiguration,
        )

        private fun <T> guarded(action: String, block: () -> T): T? = try {
            sdkCall(action, block).getOrNull()
        } catch (failure: LinkageError) {
            // sdkCall lets a LinkageError through; here it means a Kotlin plugin whose scripting API moved, which must not break the other scripts.
            LOG.warn("Could not $action", failure)
            null
        }
    }
}
