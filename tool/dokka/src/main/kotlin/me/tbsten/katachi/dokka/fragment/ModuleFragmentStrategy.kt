package me.tbsten.katachi.dokka.fragment

import me.tbsten.katachi.dokka.KatachiDokkaPlugin
import org.jetbrains.dokka.DokkaConfiguration.DokkaModuleDescription
import org.jetbrains.dokka.base.templating.parseJson
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.templates.TemplateProcessingStrategy
import java.io.File

/**
 * Collects the [FRAGMENT_FILE] of each module while the aggregating run copies the modules'
 * outputs, and keeps it out of the aggregated output.
 *
 * The paths in a fragment are relative to that module's output; here they are rebased onto the
 * aggregated output by prefixing the module's `relativePathToOutputDirectory`, the same way
 * Dokka resolves its own cross-module templates. This is the only place paths are joined.
 */
internal class ModuleFragmentStrategy(private val context: DokkaContext) : TemplateProcessingStrategy {
    private val registry by lazy { context.plugin<KatachiDokkaPlugin>().querySingle { moduleFragmentRegistry } }

    override fun process(input: File, output: File, moduleContext: DokkaModuleDescription?): Boolean {
        if (moduleContext == null || input.name != FRAGMENT_FILE) return false
        val fragment = read(input)
        when {
            fragment == null -> Unit
            fragment.schemaVersion != FRAGMENT_SCHEMA_VERSION -> context.logger.warn(
                "katachi-dokka: ${input.path} was written by another version of the plugin " +
                    "(schema ${fragment.schemaVersion}, expected $FRAGMENT_SCHEMA_VERSION), so the " +
                    "aggregated llms.txt leaves out module ${moduleContext.name}. " +
                    "Regenerate that module with the same plugin version.",
            )
            else -> registry.register(fragment.rebasedOnto(moduleContext))
        }
        // Consumed either way: the fragment is an intermediate file, not part of the site.
        return true
    }

    private fun read(input: File): ModuleFragment? = try {
        parseJson<ModuleFragment>(input.readText())
    } catch (e: Exception) {
        context.logger.warn(
            "katachi-dokka: ${input.path} could not be read (${e.message}), so the " +
                "aggregated llms.txt leaves that module out. Regenerate the module.",
        )
        null
    }

    private fun ModuleFragment.rebasedOnto(module: DokkaModuleDescription): ModuleFragment {
        val prefix = module.relativePathToOutputDirectory.invariantSeparatorsPath.trimEnd('/')
        fun rebased(path: String) = if (prefix.isEmpty()) path else "$prefix/$path"
        return copy(
            moduleName = module.name,
            featured = featured.map { entry -> entry.copy(path = rebased(entry.path)) },
            llmsFiles = llmsFiles.map(::rebased),
            modulePath = modulePath?.let(::rebased),
        )
    }
}
